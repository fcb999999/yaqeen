package app.yaqeen.feature.calendar

import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.core.HijriDate
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.DailySummaryEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranDayLogEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.data.local.TaskCompletionEntity
import app.yaqeen.domain.model.PrayerStatus
import java.time.LocalDate

/** التقويم الأساسي المعروض كشبكة شهرية. */
enum class CalendarMode { GREGORIAN, HIJRI }

/** شهر في أحد التقويمين (month 1..12). */
data class MonthKey(val year: Int, val month: Int)

data class MonthGrid(
    val mode: CalendarMode,
    val key: MonthKey,
    val title: String,
    /** وصف الشهر في التقويم الآخر (مثل "جمادى الأولى – جمادى الآخرة ١٤٤٨ هـ"). */
    val subtitle: String,
    val days: List<LocalDate>,
    /** عدد الخلايا الفارغة قبل أول يوم (الأسبوع يبدأ بالسبت). */
    val leadingBlanks: Int,
) {
    val firstDay: LocalDate get() = days.first()
    val lastDay: LocalDate get() = days.last()
}

object CalendarMath {
    const val MIN_GREGORIAN_YEAR = 1900
    const val MAX_GREGORIAN_YEAR = 2150
    const val MIN_HIJRI_YEAR = 1320
    const val MAX_HIJRI_YEAR = 1590

    /** ترتيب الأعمدة: السبت أولًا. */
    val WEEK_HEADERS = listOf("سبت", "أحد", "اثنين", "ثلاثاء", "أربعاء", "خميس", "جمعة")

    /** عدد الخلايا الفارغة قبل أول يوم في أسبوع يبدأ بالسبت (السبت 0 ... الجمعة 6). */
    fun leadingBlanks(first: LocalDate): Int = (first.dayOfWeek.value + 1) % 7

    fun monthOf(date: LocalDate, mode: CalendarMode, adjust: Int): MonthKey = when (mode) {
        CalendarMode.GREGORIAN -> MonthKey(date.year, date.monthValue)
        CalendarMode.HIJRI -> HijriCalendar.fromGregorian(date, adjust).let { MonthKey(it.year, it.month) }
    }

    fun shift(key: MonthKey, mode: CalendarMode, deltaMonths: Int): MonthKey {
        val total = key.year * 12 + (key.month - 1) + deltaMonths
        val y = Math.floorDiv(total, 12)
        val m = Math.floorMod(total, 12) + 1
        return clamp(MonthKey(y, m), mode)
    }

    fun clamp(key: MonthKey, mode: CalendarMode): MonthKey {
        val (lo, hi) = if (mode == CalendarMode.GREGORIAN) MIN_GREGORIAN_YEAR to MAX_GREGORIAN_YEAR else MIN_HIJRI_YEAR to MAX_HIJRI_YEAR
        return when {
            key.year < lo -> MonthKey(lo, 1)
            key.year > hi -> MonthKey(hi, 12)
            else -> key
        }
    }

    fun gregorianMonthTitle(year: Int, month: Int) = "${HijriCalendar.GREGORIAN_MONTHS[month - 1]} ${ArabicText.indic(year)}"
    fun hijriMonthTitle(year: Int, month: Int) = "${HijriCalendar.MONTH_NAMES[month - 1]} ${ArabicText.indic(year)} هـ"

    fun buildMonth(mode: CalendarMode, key: MonthKey, adjust: Int): MonthGrid {
        val days: List<LocalDate>
        val title: String
        val subtitle: String
        when (mode) {
            CalendarMode.GREGORIAN -> {
                val first = LocalDate.of(key.year, key.month, 1)
                days = (0 until first.lengthOfMonth()).map { first.plusDays(it.toLong()) }
                title = gregorianMonthTitle(key.year, key.month)
                val h1 = HijriCalendar.fromGregorian(days.first(), adjust)
                val h2 = HijriCalendar.fromGregorian(days.last(), adjust)
                subtitle = hijriSpan(h1, h2)
            }
            CalendarMode.HIJRI -> {
                val first = HijriCalendar.toGregorian(HijriDate(key.year, key.month, 1), adjust)
                val len = HijriCalendar.lengthOfMonth(key.year, key.month)
                days = (0 until len).map { first.plusDays(it.toLong()) }
                title = hijriMonthTitle(key.year, key.month)
                subtitle = gregorianSpan(days.first(), days.last())
            }
        }
        return MonthGrid(mode, key, title, subtitle, days, leadingBlanks(days.first()))
    }

    private fun hijriSpan(a: HijriDate, b: HijriDate): String = when {
        a.year == b.year && a.month == b.month -> "${a.monthName} ${ArabicText.indic(a.year)} هـ"
        a.year == b.year -> "${a.monthName} – ${b.monthName} ${ArabicText.indic(a.year)} هـ"
        else -> "${a.monthName} ${ArabicText.indic(a.year)} – ${b.monthName} ${ArabicText.indic(b.year)} هـ"
    }

    private fun gregorianSpan(a: LocalDate, b: LocalDate): String {
        val ma = HijriCalendar.GREGORIAN_MONTHS[a.monthValue - 1]
        val mb = HijriCalendar.GREGORIAN_MONTHS[b.monthValue - 1]
        return when {
            a.year == b.year && a.monthValue == b.monthValue -> "$ma ${ArabicText.indic(a.year)}"
            a.year == b.year -> "$ma – $mb ${ArabicText.indic(a.year)}"
            else -> "$ma ${ArabicText.indic(a.year)} – $mb ${ArabicText.indic(b.year)}"
        }
    }

    /** نص التاريخ الهجري الكامل مع اسم اليوم. */
    fun fullLabel(date: LocalDate, adjust: Int): String =
        "${HijriCalendar.dayName(date)}، ${HijriCalendar.fromGregorian(date, adjust).format()} — ${HijriCalendar.gregorianLabel(date)}"
}

// =====================================================================================
// مؤشرات الإنجاز اليومي في الشبكة وملخص الشهر
// =====================================================================================

/** ما سُجِّل في يوم (قيم مسجّلة فقط؛ غياب التسجيل لا يعني تقصيرًا). */
data class DayIndicator(
    val prayers: Int = 0,
    val rawatib: Int = 0,
    val adhkarItems: Int = 0,
    val wird: Boolean = false,
    val tasks: Int = 0,
) {
    /** عدد أنواع العبادات/الأعمال التي سُجِّل فيها شيء (0..5). */
    val categories: Int
        get() = listOf(prayers > 0, rawatib > 0, adhkarItems > 0, wird, tasks > 0).count { it }
    val hasRecord: Boolean get() = categories > 0
}

data class MonthStats(
    /** الأيام المحسوبة: أيام الشهر حتى اليوم (لا تُحتسب الأيام القادمة). */
    val daysCounted: Int,
    val daysWithRecord: Int,
    val prayersRecorded: Int,
    val adhkarDays: Int,
    val wirdDays: Int,
    val tasksDone: Int,
) {
    /** عبارة محايدة (بلا لوم): "سجّلتَ شيئًا في ١٥ يومًا من ٢٠ يومًا". */
    fun commitmentText(): String = when {
        daysCounted == 0 -> "لم يبدأ هذا الشهر بعد"
        daysWithRecord == 0 -> "لا تسجيلات في هذا الشهر حتى الآن"
        else -> "أيام فيها تسجيل: ${ArabicText.indic(daysWithRecord)} من ${ArabicText.indic(daysCounted)}"
    }
}

object CalendarStats {
    /**
     * يبني مؤشر كل يوم (مفتاحه ISO) من السجلات الأصلية، ويدمج معه ملخصًا يوميًا مخزّنًا إن وُجد (الأكبر لكل حقل).
     * المصدر الأصلي هو السجلات؛ الملخص مجرد تسريع/مزامنة بين الأجهزة.
     */
    fun indicators(
        prayers: List<PrayerLogEntity>,
        rawatib: List<RawatibLogEntity>,
        adhkar: List<AdhkarProgressEntity>,
        planLogs: List<QuranPlanLogEntity>,
        dayLogs: List<QuranDayLogEntity>,
        completions: List<TaskCompletionEntity>,
        summaries: List<DailySummaryEntity>,
    ): Map<String, DayIndicator> {
        val p = prayers.filter { !it.deleted && (it.status == PrayerStatus.MOSQUE.name || it.status == PrayerStatus.HOME.name) }
            .groupBy { it.date }.mapValues { e -> e.value.map { it.prayer }.toSet().size }
        val r = rawatib.filter { !it.deleted && it.done }.groupBy { it.date }.mapValues { e -> e.value.map { it.sunnahId }.toSet().size }
        val a = adhkar.filter { !it.deleted && (it.completed || it.count > 0) }.groupBy { it.date }.mapValues { e -> e.value.map { it.itemId }.toSet().size }
        val w = HashSet<String>()
        planLogs.filter { !it.deleted && (it.targetMet || it.ayahsRead > 0 || it.pagesRead > 0) }.forEach { w += it.date }
        dayLogs.filter { !it.deleted && (it.pagesRead > 0 || it.ayahsRead > 0) }.forEach { w += it.date }
        val t = completions.filter { !it.deleted }.groupBy { it.date }.mapValues { e -> e.value.map { it.taskId }.toSet().size }
        val s = summaries.filter { !it.deleted }.associateBy { it.date }

        val dates = HashSet<String>().apply { addAll(p.keys); addAll(r.keys); addAll(a.keys); addAll(w); addAll(t.keys); addAll(s.keys) }
        return dates.associateWith { d ->
            val sum = s[d]
            DayIndicator(
                prayers = maxOf(p[d] ?: 0, sum?.let { it.prayersMosque + it.prayersHome } ?: 0),
                rawatib = maxOf(r[d] ?: 0, sum?.rawatibDone ?: 0),
                adhkarItems = maxOf(
                    a[d] ?: 0,
                    sum?.let { maxOf(it.adhkarItemsCompleted, if (it.adhkarMorningDone || it.adhkarEveningDone || it.adhkarSleepDone) 1 else 0) } ?: 0,
                ),
                wird = (d in w) || (sum?.let { it.quranPlanTargetMet || it.quranPages > 0 || it.quranAyahs > 0 } ?: false),
                tasks = maxOf(t[d] ?: 0, sum?.tasksDone ?: 0),
            )
        }.filterValues { it.hasRecord }
    }

    fun monthStats(days: List<LocalDate>, indicators: Map<String, DayIndicator>, today: LocalDate): MonthStats {
        val counted = days.filter { !it.isAfter(today) }
        var withRecord = 0
        var prayers = 0
        var adhkarDays = 0
        var wirdDays = 0
        var tasks = 0
        for (d in counted) {
            val i = indicators[d.toString()] ?: continue
            if (i.hasRecord) withRecord++
            prayers += i.prayers
            if (i.adhkarItems > 0) adhkarDays++
            if (i.wird) wirdDays++
            tasks += i.tasks
        }
        return MonthStats(counted.size, withRecord, prayers, adhkarDays, wirdDays, tasks)
    }
}
