package app.yaqeen.feature.tasks

import app.yaqeen.core.ArabicText
import java.time.LocalDate
import java.time.LocalTime

/** تنسيقات عربية خالصة (بلا إطار Android) تُستخدم في المهام والتقويم وتُختبر وحدويًا. */
object ArabicFormat {
    fun num(n: Int): String = ArabicText.indic(n)

    /** يحلّل "HH:mm" (يقبل "5:30") ويعيد null إن كان غير صالح. */
    fun parseTime(hhmm: String?): LocalTime? {
        if (hhmm.isNullOrBlank()) return null
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    fun formatHHmm(h: Int, m: Int): String = h.toString().padStart(2, '0') + ":" + m.toString().padStart(2, '0')

    /** "17:30" -> "٥:٣٠ م". يعيد null إن كان الإدخال غير صالح. */
    fun time12(hhmm: String?): String? {
        val t = parseTime(hhmm) ?: return null
        val suffix = if (t.hour < 12) "ص" else "م"
        val h = when {
            t.hour == 0 -> 12
            t.hour > 12 -> t.hour - 12
            else -> t.hour
        }
        return "${ArabicText.indic(h)}:${ArabicText.indic(t.minute.toString().padStart(2, '0'))} $suffix"
    }

    /**
     * عدد مع المعدود حسب قواعد العربية: 1 مفرد، 2 مثنى، 3..10 جمع قلة، وما فوق تمييز منصوب.
     * مثال: count(3, "صفحة واحدة", "صفحتان", "صفحات", "صفحة") = "٣ صفحات".
     */
    fun count(n: Int, one: String, two: String, few: String, many: String): String = when {
        n == 1 -> one
        n == 2 -> two
        n in 3..10 -> "${ArabicText.indic(n)} $few"
        else -> "${ArabicText.indic(n)} $many"
    }

    fun days(n: Int) = count(n, "يوم واحد", "يومان", "أيام", "يومًا")
    fun pages(n: Int) = count(n, "صفحة واحدة", "صفحتان", "صفحات", "صفحة")
    fun ayahs(n: Int) = count(n, "آية واحدة", "آيتان", "آيات", "آية")
    fun juz(n: Int) = count(n, "جزء واحد", "جزءان", "أجزاء", "جزءًا")
    fun tasks(n: Int) = count(n, "مهمة واحدة", "مهمتان", "مهام", "مهمة")
    fun prayers(n: Int) = count(n, "صلاة واحدة", "صلاتان", "صلوات", "صلاة")

    /** الوقت المتبقي نسبةً إلى اليوم: 0 اليوم، 1 غدًا، 3 "بعد ٣ أيام"، -1 أمس. */
    fun relativeDays(n: Int): String = when {
        n == 0 -> "اليوم"
        n == 1 -> "غدًا"
        n == 2 -> "بعد يومين"
        n in 3..10 -> "بعد ${ArabicText.indic(n)} أيام"
        n > 10 -> "بعد ${ArabicText.indic(n)} يومًا"
        n == -1 -> "أمس"
        n == -2 -> "قبل يومين"
        n in -10..-3 -> "قبل ${ArabicText.indic(-n)} أيام"
        else -> "قبل ${ArabicText.indic(-n)} يومًا"
    }

    fun parseDate(s: String?): LocalDate? = if (s.isNullOrBlank()) null else runCatching { LocalDate.parse(s.trim()) }.getOrNull()
}
