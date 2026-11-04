package app.yaqeen.feature.prayer.tracking

import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.Sunnah
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/*
 * === حسابات إحصاءات الصلاة والسنن (منطق خالص بلا Android؛ تعيد استخدامه شاشات الرحلة والرئيسية) ===
 *
 * القاعدة الأساسية: غياب التسجيل ≠ ترك الصلاة. الصلاة غير المسجّلة تُحتسب "غير مسجّلة" فقط ولا تدخل في
 * أي نسبة تقصير. "لم أصلِّ بعد" (NOT_PRAYED) حالة يسجّلها المستخدم بنفسه ويُعدّها وحدها.
 *
 * الواجهة العامة:
 *  - prayerStats(logs, from, to, asOf)  -> PrayerRangeStats
 *  - rawatibStats(logs, from, to, asOf) -> RawatibRangeStats
 *  - StatsRanges: حدود الفترات (يوم/أسبوع/شهر/7/30/90 يومًا)
 */

/** تفصيل صلاة واحدة عبر فترة. */
data class PrayerBreakdown(
    val prayer: Prayer,
    val mosque: Int,
    val home: Int,
    val notPrayed: Int,
    /** عدد أيام الفترة التي لم تُسجَّل فيها هذه الصلاة. */
    val unrecorded: Int,
) {
    val recorded: Int get() = mosque + home + notPrayed
    val prayed: Int get() = mosque + home
}

/** حالات صلوات يوم واحد (الصلوات غير المسجّلة غائبة من الخريطة). */
data class PrayerDayStats(val date: LocalDate, val statuses: Map<Prayer, PrayerStatus>) {
    val recorded: Int get() = statuses.size
    val mosque: Int get() = statuses.count { it.value == PrayerStatus.MOSQUE }
    val home: Int get() = statuses.count { it.value == PrayerStatus.HOME }
    val notPrayed: Int get() = statuses.count { it.value == PrayerStatus.NOT_PRAYED }
    val prayed: Int get() = mosque + home
}

/**
 * إحصاءات الصلوات في فترة. [from]..[to] هي الفترة الفعلية بعد اقتطاعها عند [asOf] (لا تُحسب الأيام المستقبلية).
 * إن كانت الفترة فارغة (من بعد إلى) فإن [days] = 0.
 */
data class PrayerRangeStats(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    /** عدد الصلوات الممكنة تسجيلها = الأيام × ٥. */
    val possible: Int,
    val recorded: Int,
    val mosque: Int,
    val home: Int,
    val notPrayed: Int,
    val perPrayer: List<PrayerBreakdown>,
    /** تصاعديًا حسب التاريخ، يوم لكل يوم في الفترة الفعلية (حتى لو بلا تسجيل). */
    val perDay: List<PrayerDayStats>,
) {
    /** الصلوات المسجّلة على أنها أُدّيت (مسجد + بيت). */
    val prayed: Int get() = mosque + home

    /** عدد الصلوات التي لم تُسجَّل (محايد، لا يعني تركها). */
    val unrecorded: Int get() = (possible - recorded).coerceAtLeast(0)

    /** نسبة الجماعة = مسجد ÷ (مسجد + بيت) من الصلوات المؤداة المسجّلة. null إذا لم تُسجَّل أي صلاة مؤداة. */
    val jamaaRatio: Float? get() = if (prayed == 0) null else mosque.toFloat() / prayed

    /** نسبة الجماعة مقرّبة 0..100، أو null عند انعدام المقام. */
    val jamaaPercent: Int? get() = jamaaRatio?.let { (it * 100f).roundToInt() }

    /** تغطية التسجيل 0..1 = المسجّل ÷ الممكن. */
    val coverage: Float get() = if (possible == 0) 0f else recorded.toFloat() / possible

    val hasRecords: Boolean get() = recorded > 0

    /** أيام فيها تسجيل واحد على الأقل. */
    val daysWithRecords: Int get() = perDay.count { it.recorded > 0 }

    /** أيام سُجّلت فيها الصلوات الخمس كلها. */
    val fullyRecordedDays: Int get() = perDay.count { it.recorded == Prayer.entries.size }
}

private val PRAYER_COUNT = Prayer.entries.size

private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()

/**
 * يحسب إحصاءات الصلوات للفترة [from]..[to] (شاملة الطرفين).
 *  - الصفوف المحذوفة (deleted=true) وغير المعروفة (صلاة/حالة/تاريخ غير صالح) وخارج الفترة تُتجاهل.
 *  - إن تكرّر (تاريخ، صلاة) يؤخذ الأحدث updatedAt.
 *  - [asOf]: إن حُدِّد تُقتطع الفترة عنده فلا تُحسب الأيام المستقبلية ضمن "الممكن".
 */
fun prayerStats(
    logs: List<PrayerLogEntity>,
    from: LocalDate,
    to: LocalDate,
    asOf: LocalDate? = null,
): PrayerRangeStats {
    val end = if (asOf != null && asOf.isBefore(to)) asOf else to
    if (end.isBefore(from)) {
        return PrayerRangeStats(
            from, to, 0, 0, 0, 0, 0, 0,
            Prayer.entries.map { PrayerBreakdown(it, 0, 0, 0, 0) },
            emptyList(),
        )
    }
    val dayCount = (ChronoUnit.DAYS.between(from, end) + 1).toInt()

    // (تاريخ، صلاة) -> أحدث سجل صالح
    val latest = HashMap<Pair<LocalDate, Prayer>, PrayerLogEntity>()
    for (row in logs) {
        if (row.deleted) continue
        val date = parseDate(row.date) ?: continue
        if (date.isBefore(from) || date.isAfter(end)) continue
        val prayer = Prayer.entries.firstOrNull { it.name == row.prayer } ?: continue
        if (PrayerStatus.entries.none { it.name == row.status }) continue
        val key = date to prayer
        val prev = latest[key]
        if (prev == null || row.updatedAt >= prev.updatedAt) latest[key] = row
    }

    val byDay = HashMap<LocalDate, MutableMap<Prayer, PrayerStatus>>()
    for ((key, row) in latest) {
        byDay.getOrPut(key.first) { LinkedHashMap() }[key.second] = PrayerStatus.valueOf(row.status)
    }

    val perDay = ArrayList<PrayerDayStats>(dayCount)
    var d = from
    repeat(dayCount) {
        val statuses = byDay[d].orEmpty().toSortedMap(compareBy<Prayer> { it.ordinal })
        perDay += PrayerDayStats(d, statuses)
        d = d.plusDays(1)
    }

    val perPrayer = Prayer.entries.map { p ->
        var m = 0; var h = 0; var n = 0
        for (day in perDay) when (day.statuses[p]) {
            PrayerStatus.MOSQUE -> m++
            PrayerStatus.HOME -> h++
            PrayerStatus.NOT_PRAYED -> n++
            null -> Unit
        }
        PrayerBreakdown(p, m, h, n, dayCount - (m + h + n))
    }

    return PrayerRangeStats(
        from = from,
        to = end,
        days = dayCount,
        possible = dayCount * PRAYER_COUNT,
        recorded = perPrayer.sumOf { it.recorded },
        mosque = perPrayer.sumOf { it.mosque },
        home = perPrayer.sumOf { it.home },
        notPrayed = perPrayer.sumOf { it.notPrayed },
        perPrayer = perPrayer,
        perDay = perDay,
    )
}

// ----------------------------------------------------------------- السنن الرواتب

/** أيام وركعات سنة واحدة عبر فترة. */
data class SunnahCount(val sunnahId: String, val days: Int, val rakaat: Int)

data class RawatibDayStats(val date: LocalDate, val doneIds: Set<String>, val rakaat: Int) {
    /** عدد السنن الراتبة الافتراضية (الخمس) المنجزة في اليوم. */
    val defaultDone: Int get() = Sunnah.entries.count { it.name in doneIds }
    val allDefaultsDone: Boolean get() = defaultDone == Sunnah.entries.size
}

/**
 * إحصاءات السنن الرواتب في فترة.
 *  - [daysCompleted]: أيام أُنجزت فيها السنن الراتبة الخمس الافتراضية كلها.
 *  - [daysWithAny]: أيام أُنجزت فيها سنة واحدة على الأقل (تشمل المخصصة).
 *  - [totalRakaat]: مجموع ركعات السنن المسجّلة المنجزة (الافتراضية والمخصصة).
 */
data class RawatibRangeStats(
    val from: LocalDate,
    val to: LocalDate,
    val days: Int,
    val daysWithAny: Int,
    val daysCompleted: Int,
    val totalDone: Int,
    val totalRakaat: Int,
    /** الافتراضية بترتيبها ثم المخصصة بترتيب المعرّف؛ تُدرج فقط السنن التي سُجّلت مرة على الأقل. */
    val perSunnah: List<SunnahCount>,
    val perDay: List<RawatibDayStats>,
) {
    val hasRecords: Boolean get() = totalDone > 0
}

/** ركعات سجل منجز: القيمة المخزّنة إن وُجدت وإلا عدد الركعات الافتراضي للسنة المعروفة. */
internal fun rakaatOf(row: RawatibLogEntity): Int {
    if (row.rakaat > 0) return row.rakaat
    return Sunnah.entries.firstOrNull { it.name == row.sunnahId }?.rakaat ?: 0
}

/**
 * يحسب إحصاءات السنن للفترة [from]..[to] (شاملة). الصفوف المحذوفة وغير المنجزة (done=false) وخارج الفترة تُتجاهل.
 * [asOf] يقتطع الفترة كما في [prayerStats].
 */
fun rawatibStats(
    logs: List<RawatibLogEntity>,
    from: LocalDate,
    to: LocalDate,
    asOf: LocalDate? = null,
): RawatibRangeStats {
    val end = if (asOf != null && asOf.isBefore(to)) asOf else to
    if (end.isBefore(from)) return RawatibRangeStats(from, to, 0, 0, 0, 0, 0, emptyList(), emptyList())
    val dayCount = (ChronoUnit.DAYS.between(from, end) + 1).toInt()

    val latest = HashMap<Pair<LocalDate, String>, RawatibLogEntity>()
    for (row in logs) {
        if (row.deleted || row.sunnahId.isBlank()) continue
        val date = parseDate(row.date) ?: continue
        if (date.isBefore(from) || date.isAfter(end)) continue
        val key = date to row.sunnahId
        val prev = latest[key]
        if (prev == null || row.updatedAt >= prev.updatedAt) latest[key] = row
    }
    val done = latest.filterValues { it.done }

    val idsByDay = HashMap<LocalDate, MutableSet<String>>()
    val rakaatByDay = HashMap<LocalDate, Int>()
    val perSunnahDays = HashMap<String, Int>()
    val perSunnahRakaat = HashMap<String, Int>()
    for ((key, row) in done) {
        val (date, id) = key
        val r = rakaatOf(row)
        idsByDay.getOrPut(date) { LinkedHashSet() } += id
        rakaatByDay[date] = (rakaatByDay[date] ?: 0) + r
        perSunnahDays[id] = (perSunnahDays[id] ?: 0) + 1
        perSunnahRakaat[id] = (perSunnahRakaat[id] ?: 0) + r
    }

    val perDay = ArrayList<RawatibDayStats>(dayCount)
    var d = from
    repeat(dayCount) {
        perDay += RawatibDayStats(d, idsByDay[d].orEmpty(), rakaatByDay[d] ?: 0)
        d = d.plusDays(1)
    }

    val defaultOrder = Sunnah.entries.map { it.name }
    val ids = perSunnahDays.keys.sortedWith(
        compareBy<String> { defaultOrder.indexOf(it).let { i -> if (i < 0) Int.MAX_VALUE else i } }.thenBy { it },
    )
    return RawatibRangeStats(
        from = from,
        to = end,
        days = dayCount,
        daysWithAny = perDay.count { it.doneIds.isNotEmpty() },
        daysCompleted = perDay.count { it.allDefaultsDone },
        totalDone = done.size,
        totalRakaat = rakaatByDay.values.sum(),
        perSunnah = ids.map { SunnahCount(it, perSunnahDays.getValue(it), perSunnahRakaat.getValue(it)) },
        perDay = perDay,
    )
}

// ----------------------------------------------------------------- الفترات

enum class StatsPeriod(val ar: String) {
    DAY("يوم"),
    WEEK("أسبوع"),
    MONTH("شهر"),
    LAST_7("٧ أيام"),
    LAST_30("٣٠ يومًا"),
    LAST_90("٩٠ يومًا");

    /** الفترات المتحرّكة تنتهي دائمًا اليوم ولا يُتنقَّل بينها. */
    val isRolling: Boolean get() = this == LAST_7 || this == LAST_30 || this == LAST_90
}

data class DateRange(val from: LocalDate, val to: LocalDate) {
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt().coerceAtLeast(0)
}

object StatsRanges {
    /** بداية الأسبوع في الاستعمال العربي: السبت. */
    val WEEK_START: DayOfWeek = DayOfWeek.SATURDAY

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(WEEK_START))

    /** حدود الفترة. [anchor] تاريخ مرجعي لفترات يوم/أسبوع/شهر؛ أما المتحركة فتنتهي عند [today]. */
    fun range(period: StatsPeriod, anchor: LocalDate, today: LocalDate): DateRange = when (period) {
        StatsPeriod.DAY -> DateRange(anchor, anchor)
        StatsPeriod.WEEK -> weekStart(anchor).let { DateRange(it, it.plusDays(6)) }
        StatsPeriod.MONTH -> YearMonth.from(anchor).let { DateRange(it.atDay(1), it.atEndOfMonth()) }
        StatsPeriod.LAST_7 -> DateRange(today.minusDays(6), today)
        StatsPeriod.LAST_30 -> DateRange(today.minusDays(29), today)
        StatsPeriod.LAST_90 -> DateRange(today.minusDays(89), today)
    }

    /** المرجع بعد التنقل [steps] فترات (سالب = أقدم). المتحركة لا تتغير. */
    fun shift(period: StatsPeriod, anchor: LocalDate, steps: Int): LocalDate = when (period) {
        StatsPeriod.DAY -> anchor.plusDays(steps.toLong())
        StatsPeriod.WEEK -> anchor.plusWeeks(steps.toLong())
        StatsPeriod.MONTH -> anchor.plusMonths(steps.toLong())
        else -> anchor
    }

    /** هل توجد فترة أحدث تبدأ ضمن الأيام الماضية أو اليوم؟ */
    fun canGoNext(period: StatsPeriod, anchor: LocalDate, today: LocalDate): Boolean =
        !period.isRolling && !range(period, shift(period, anchor, 1), today).from.isAfter(today)
}
