package app.yaqeen.feature.prayer.adhan

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager

/**
 * تجربة الصوت داخل التطبيق (زر "تجربة"): يشغّل الصوت المختار على مستوى صوت الوسائط.
 * الصوت الافتراضي = نغمة إشعارات الجهاز. يُوقَف عند الإغلاق.
 */
class AdhanPreview(context: Context) {
    private val app = context.applicationContext
    private var player: MediaPlayer? = null
    private var ringtone: Ringtone? = null

    val isRingtonePlaying: Boolean get() = ringtone?.isPlaying == true

    /** يبدأ التشغيل؛ يعيد false إن تعذّر. [onEnd] يُستدعى عند انتهاء ملف الصوت. */
    fun play(voice: String, onEnd: () -> Unit): Boolean {
        stop()
        return try {
            when (val ref = AdhanVoices.parse(voice)) {
                VoiceRef.Default -> {
                    val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    val r = RingtoneManager.getRingtone(app, uri) ?: return false
                    ringtone = r
                    r.play()
                    true
                }
                is VoiceRef.File -> {
                    val f = AdhanVoices.resolveFile(app, ref.name) ?: return false
                    start { it.setDataSource(f.absolutePath) }.also { ok -> if (ok) player?.setOnCompletionListener { stop(); onEnd() } }
                }
                is VoiceRef.Raw -> {
                    val id = AdhanVoices.resolveRawId(app, ref.name)
                    if (id == 0) return false
                    start { mp -> app.resources.openRawResourceFd(id).use { mp.setDataSource(it.fileDescriptor, it.startOffset, it.length) } }
                        .also { ok -> if (ok) player?.setOnCompletionListener { stop(); onEnd() } }
                }
            }
        } catch (e: Exception) {
            stop()
            false
        }
    }

    private fun start(source: (MediaPlayer) -> Unit): Boolean {
        val mp = MediaPlayer()
        player = mp
        mp.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
        source(mp)
        mp.setOnPreparedListener { it.start() }
        mp.setOnErrorListener { _, _, _ -> stop(); true }
        mp.prepareAsync()
        return true
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; runCatching { it.release() } }
        player = null
        ringtone?.let { runCatching { it.stop() } }
        ringtone = null
    }
}
