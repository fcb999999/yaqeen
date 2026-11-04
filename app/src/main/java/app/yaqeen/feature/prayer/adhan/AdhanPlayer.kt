package app.yaqeen.feature.prayer.adhan

import android.content.Context
import android.media.AudioManager
import androidx.core.content.ContextCompat
import app.yaqeen.notifications.ReminderPayload

/**
 * يعالج تذكير قناة الأذان (تشغيل الصوت المختار وعرض الإشعار). يعيد true إن عالجه بالكامل،
 * وfalse ليعرض النظام الإشعار الافتراضي بصوت القناة.
 *
 * - الصوت "default" (أو أي صوت مخصص غير متاح/ملف محذوف): false → إشعار القناة بنغمة النظام.
 * - صوت مخصص: خدمة أمامية قصيرة تشغّل الملف مع إشعار فيه زر "إيقاف" → true.
 * - إن كان الجهاز في وضع الصامت/الاهتزاز نحترمه: false (إشعار عادي بلا صوت أذان) لئلا يُفاجأ المصلّون في المسجد.
 * الصوت المختار يصل في payload.extras["voice"] لحظة الجدولة (لا قراءة من قاعدة البيانات داخل المستقبِل).
 */
object AdhanPlayer {
    fun handle(context: Context, payload: ReminderPayload): Boolean {
        val voice = payload.extras["voice"] ?: return false
        if (!AdhanVoices.isCustomPlayable(context, voice)) return false
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (audio != null && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return false
        return try {
            ContextCompat.startForegroundService(context, AdhanService.playIntent(context, payload.title, payload.text, payload.route, voice))
            true
        } catch (e: Exception) {
            // مثلًا منع النظام بدء خدمة أمامية من الخلفية: نرجع للإشعار الافتراضي.
            false
        }
    }
}
