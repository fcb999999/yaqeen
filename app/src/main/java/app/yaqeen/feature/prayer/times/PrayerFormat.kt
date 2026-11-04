package app.yaqeen.feature.prayer.times

import app.yaqeen.core.ArabicText
import java.time.ZonedDateTime

/** تنسيقات عرض خالصة (قابلة للاختبار) للأوقات والمدد بالعربية. */
object PrayerFormat {
    /** "٤:٥٢ ص" (نظام ١٢ ساعة) أو "٠٤:٥٢" (نظام ٢٤ ساعة). */
    fun clock(t: ZonedDateTime, is24h: Boolean = false): String {
        val h = t.hour
        val m = t.minute
        val mm = m.toString().padStart(2, '0')
        return if (is24h) {
            ArabicText.indic("${h.toString().padStart(2, '0')}:$mm")
        } else {
            val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
            ArabicText.indic("$h12:$mm") + (if (h < 12) " ص" else " م")
        }
    }

    /** عدّاد تنازلي "٠٢:١٤:٣٣" من عدد الثواني (لا يقل عن صفر). */
    fun countdown(totalSeconds: Long): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        fun two(x: Long) = x.toString().padStart(2, '0')
        return ArabicText.indic("${two(h)}:${two(m)}:${two(sec)}")
    }

    /** "دقيقة" / "دقيقتان" / "٣ دقائق" / "١١ دقيقة" بحسب قواعد العدد في العربية. */
    fun minutes(n: Int): String = when {
        n == 1 -> "دقيقة"
        n == 2 -> "دقيقتان"
        n in 3..10 -> "${ArabicText.indic(n)} دقائق"
        else -> "${ArabicText.indic(n)} دقيقة"
    }

    /** نص الإزاحة بالدقائق للعرض في إعدادات التعديل: "+٢ د" أو "−٣ د" أو "بدون تعديل". */
    fun offset(n: Int): String = when {
        n == 0 -> "بدون تعديل"
        n > 0 -> "+${ArabicText.indic(n)} د"
        else -> "−${ArabicText.indic(-n)} د"
    }

    /** المسافة بالكيلومتر بأرقام هندية مع فاصل آلاف. */
    fun km(distance: Double): String {
        val v = distance.toLong()
        val s = v.toString().reversed().chunked(3).joinToString("٬").reversed()
        return ArabicText.indic(s) + " كم"
    }

    /** زاوية بأرقام هندية "٢٤٤٫٨°". */
    fun degrees(deg: Double): String = ArabicText.indic("%.1f".format(java.util.Locale.ROOT, deg)).replace('.', '٫') + "°"
}
