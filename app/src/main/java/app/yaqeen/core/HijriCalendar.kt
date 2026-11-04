package app.yaqeen.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.chrono.HijrahChronology
import java.time.chrono.HijrahDate
import java.time.temporal.ChronoField

/** تاريخ هجري (أم القرى) مع إمكانية التعديل اليدوي بالأيام لاختلاف رؤية الهلال. */
data class HijriDate(val year: Int, val month: Int, val day: Int) {
    val monthName: String get() = HijriCalendar.MONTH_NAMES[month - 1]
    fun format(indic: Boolean = true): String {
        val d = if (indic) ArabicText.indic(day) else day.toString()
        val y = if (indic) ArabicText.indic(year) else year.toString()
        return "$d $monthName $y هـ"
    }
}

object HijriCalendar {
    val MONTH_NAMES = listOf(
        "محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
        "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة",
    )

    val DAY_NAMES = mapOf(
        DayOfWeek.SATURDAY to "السبت", DayOfWeek.SUNDAY to "الأحد", DayOfWeek.MONDAY to "الاثنين",
        DayOfWeek.TUESDAY to "الثلاثاء", DayOfWeek.WEDNESDAY to "الأربعاء", DayOfWeek.THURSDAY to "الخميس",
        DayOfWeek.FRIDAY to "الجمعة",
    )

    val GREGORIAN_MONTHS = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    /** التاريخ الهجري المقابل لتاريخ ميلادي. adjustDays: تعديل المستخدم (+1 إذا كان الهجري عنده أسبق بيوم...). */
    fun fromGregorian(date: LocalDate, adjustDays: Int = 0): HijriDate {
        val h = HijrahChronology.INSTANCE.date(date.plusDays(adjustDays.toLong()))
        return HijriDate(h.get(ChronoField.YEAR), h.get(ChronoField.MONTH_OF_YEAR), h.get(ChronoField.DAY_OF_MONTH))
    }

    /** التاريخ الميلادي المقابل لتاريخ هجري. */
    fun toGregorian(h: HijriDate, adjustDays: Int = 0): LocalDate =
        LocalDate.from(HijrahDate.of(h.year, h.month, h.day)).minusDays(adjustDays.toLong())

    fun lengthOfMonth(year: Int, month: Int): Int = HijrahDate.of(year, month, 1).lengthOfMonth()

    fun gregorianLabel(date: LocalDate): String =
        "${ArabicText.indic(date.dayOfMonth)} ${GREGORIAN_MONTHS[date.monthValue - 1]} ${ArabicText.indic(date.year)}"

    fun dayName(date: LocalDate): String = DAY_NAMES.getValue(date.dayOfWeek)
}
