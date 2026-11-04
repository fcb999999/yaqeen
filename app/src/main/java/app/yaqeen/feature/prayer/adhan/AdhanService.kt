package app.yaqeen.feature.prayer.adhan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import app.yaqeen.MainActivity
import app.yaqeen.R

/**
 * خدمة أمامية قصيرة العمر لتشغيل صوت أذان مخصص (ملف مستورد) عند دخول الوقت.
 * تعمل فقط طوال مدة الصوت (مدته ≤ ١٠ دقائق) ثم تتوقف؛ لا خدمات دائمة.
 */
class AdhanService : Service() {
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val safetyStop = Runnable { finishPlayback(userStopped = false) }
    private var title = ""
    private var text = ""
    private var route: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                finishPlayback(userStopped = true)
                return START_NOT_STICKY
            }
            ACTION_PLAY -> {
                title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                text = intent.getStringExtra(EXTRA_TEXT).orEmpty()
                route = intent.getStringExtra(EXTRA_ROUTE)
                val voice = intent.getStringExtra(EXTRA_VOICE)
                // يجب استدعاء startForeground فورًا بعد startForegroundService.
                ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(playing = true), foregroundType())
                if (!startPlayback(voice)) finishPlayback(userStopped = false)
            }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun foregroundType() = if (android.os.Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0

    private fun startPlayback(voice: String?): Boolean {
        releasePlayer()
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { change ->
                // فقدان التركيز الدائم أو المؤقت (مكالمة مثلًا): نوقف الأذان بدل التشويش.
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    handler.post { finishPlayback(userStopped = false) }
                }
            }
            .build()
        focusRequest = req
        am.requestAudioFocus(req)
        return try {
            val mp = MediaPlayer()
            player = mp
            mp.setAudioAttributes(attrs)
            mp.setWakeMode(applicationContext, PowerManager.PARTIAL_WAKE_LOCK)
            when (val ref = AdhanVoices.parse(voice)) {
                is VoiceRef.File -> mp.setDataSource((AdhanVoices.resolveFile(this, ref.name) ?: return false).absolutePath)
                is VoiceRef.Raw -> {
                    val id = AdhanVoices.resolveRawId(this, ref.name)
                    if (id == 0) return false
                    resources.openRawResourceFd(id).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                }
                VoiceRef.Default -> return false
            }
            mp.setOnPreparedListener { it.start() }
            mp.setOnCompletionListener { finishPlayback(userStopped = false) }
            mp.setOnErrorListener { _, _, _ -> finishPlayback(userStopped = false); true }
            mp.prepareAsync()
            handler.postDelayed(safetyStop, SAFETY_TIMEOUT_MS)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun finishPlayback(userStopped: Boolean) {
        handler.removeCallbacks(safetyStop)
        releasePlayer()
        if (userStopped) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            NotificationManagerCompat.from(this).cancel(NOTIF_ID)
        } else {
            // يبقى إشعار وقت الصلاة ظاهرًا بعد انتهاء الصوت (بلا زر إيقاف).
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
            runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, buildNotification(playing = false)) }
        }
        stopSelf()
    }

    private fun releasePlayer() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        focusRequest?.let { (getSystemService(Context.AUDIO_SERVICE) as AudioManager).abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(safetyStop)
        releasePlayer()
        super.onDestroy()
    }

    private fun buildNotification(playing: Boolean): Notification {
        ensureChannel(this)
        val open = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ROUTE, route)
        val contentPi = PendingIntent.getActivity(this, NOTIF_ID, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = NotificationCompat.Builder(this, CHANNEL_PLAYING)
            .setSmallIcon(R.drawable.ic_logo_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentPi)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(playing)
            .setAutoCancel(!playing)
        if (playing) {
            val stop = Intent(this, AdhanService::class.java).setAction(ACTION_STOP)
            val stopPi = PendingIntent.getService(this, NOTIF_ID + 1, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            b.addAction(0, getString(R.string.adhan_stop), stopPi)
        }
        return b.build()
    }

    companion object {
        const val ACTION_PLAY = "app.yaqeen.adhan.PLAY"
        const val ACTION_STOP = "app.yaqeen.adhan.STOP"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_ROUTE = "route"
        const val EXTRA_VOICE = "voice"
        private const val NOTIF_ID = 7101
        private const val CHANNEL_PLAYING = "adhan_playing"
        private const val SAFETY_TIMEOUT_MS = 11L * 60 * 1000

        /** قناة صامتة لإشعار التشغيل (الصوت يأتي من المشغّل لا من القناة، فلا يتكرر صوت النظام). */
        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(CHANNEL_PLAYING, context.getString(R.string.channel_adhan_playing), NotificationManager.IMPORTANCE_LOW)
            ch.setSound(null, null)
            ch.enableVibration(false)
            nm.createNotificationChannel(ch)
        }

        fun playIntent(context: Context, title: String, text: String, route: String?, voice: String): Intent =
            Intent(context, AdhanService::class.java).setAction(ACTION_PLAY)
                .putExtra(EXTRA_TITLE, title).putExtra(EXTRA_TEXT, text).putExtra(EXTRA_ROUTE, route).putExtra(EXTRA_VOICE, voice)
    }
}
