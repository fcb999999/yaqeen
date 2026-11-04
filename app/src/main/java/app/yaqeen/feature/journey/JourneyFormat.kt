package app.yaqeen.feature.journey

import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import java.time.LocalDate
import java.util.Locale

/** تنسيق نصوص الإحصاءات (أرقام هندية، فاصل عشري عربي). دوال خالصة. */
object JourneyFormat {
    fun n(v: Int): String = ArabicText.indic(v)

    /** نسبة مئوية صحيحة: ٧٥٪ */
    fun percent(ratio: Double): String = ArabicText.indic(Math.round(ratio * 100).toInt()) + "٪"

    /** رقم بمنزلة عشرية واحدة: ٣٫٢ */
    fun decimal1(v: Double): String = ArabicText.indic(String.format(Locale.US, "%.1f", v)).replace('.', '٫')

    /** ٥ نوفمبر */
    fun shortDate(d: LocalDate): String = "${ArabicText.indic(d.dayOfMonth)} ${HijriCalendar.GREGORIAN_MONTHS[d.monthValue - 1]}"

    fun fullDate(d: LocalDate): String = "${HijriCalendar.dayName(d)} ${shortDate(d)}"

    private val SHORT_DAYS = mapOf(
        java.time.DayOfWeek.SATURDAY to "سبت", java.time.DayOfWeek.SUNDAY to "أحد", java.time.DayOfWeek.MONDAY to "اثنين",
        java.time.DayOfWeek.TUESDAY to "ثلاثاء", java.time.DayOfWeek.WEDNESDAY to "أربعاء",
        java.time.DayOfWeek.THURSDAY to "خميس", java.time.DayOfWeek.FRIDAY to "جمعة",
    )

    fun shortDayName(d: LocalDate): String = SHORT_DAYS.getValue(d.dayOfWeek)

    /** ٥ نوفمبر إلى ١١ نوفمبر */
    fun range(from: LocalDate, to: LocalDate): String =
        if (from == to) shortDate(from) else "${shortDate(from)} إلى ${shortDate(to)}"

    fun periodTitle(days: Int): String = when (days) {
        7 -> "آخر ٧ أيام"
        30 -> "آخر ٣٠ يومًا"
        90 -> "آخر ٩٠ يومًا"
        else -> "آخر ${ArabicNoun.DAY.count(days) { ArabicText.indic(it) }}"
    }
}
