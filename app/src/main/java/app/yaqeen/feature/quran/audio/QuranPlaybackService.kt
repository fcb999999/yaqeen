package app.yaqeen.feature.quran.audio

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import app.yaqeen.MainActivity
import app.yaqeen.R

/**
 * خدمة تشغيل التلاوة: يعيش فيها ExoPlayer وجلسة الوسائط، فتستمر التلاوة والتطبيق في الخلفية،
 * وتظهر أزرار التحكم (السابق/إيقاف/التالي) في إشعار الوسائط وشاشة القفل.
 * [QuranAudioController] عميل رقيق يتصل بها عبر MediaController ولا يملك المشغّل بنفسه.
 */
class QuranPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).setUsage(C.USAGE_MEDIA).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, player).setId("yaqeen_quran").setSessionActivity(openApp).build()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(CHANNEL_ID)
                .setChannelName(R.string.audio_channel_name)
                .build()
                .also { it.setSmallIcon(R.drawable.ic_logo_foreground) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** عند إغلاق التطبيق من قائمة التطبيقات: تستمر التلاوة إن كانت جارية، وإلا تتوقف الخدمة. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "quran_audio"
    }
}
