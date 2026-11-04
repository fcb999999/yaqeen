package app.yaqeen.feature.journey

import app.yaqeen.data.local.DailySummaryEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.domain.model.QuranPlanStatus
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * كل حسابات "رحلتي" الخالصة. لا Android ولا Compose ولا I/O، وحسابات التواريخ بـ LocalDate فقط (لا تتأثر بتوقيت صيفي).
 *
 * قاعدة المنتج الأساسية: ما لم يُسجَّل لا يُحسب "لم يحدث". المقامات في النسب هي دائمًا "أيام سُجّل فيها شيء"،
 * وتُعرض صراحةً في الواجهة.
 */

/** فترة مغلقة من التواريخ [from, to]. */
data class PeriodRange(val from: LocalDate, val to: LocalDate) {
    init { require(!to.isBefore(from)) { "نهاية الفترة قبل بدايتها" } }
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
    operator fun contains(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)

    /** الفترة السابقة مباشرة بالطول نفسه. */
    fun previous(): PeriodRange = PeriodRange(from.minusDays(days.toLong()), from.minusDays(1))

    companion object {
        /** آخر [days] أيام تنتهي باليوم [today] (شاملةً اليوم). */
        fun last(today: LocalDate, days: Int) = PeriodRange(today.minusDays((days - 1).toLong()), today)
    }
}

// ============================================================ أرقام عربية بالمفرد والمثنى والجمع

/** صيغ العدّ العربي: واحد، اثنان (مجرور)، 3..10، 11 فأكثر. تُستعمل بعد حرف جر (في/بـ/من) فلذلك المثنى مجرور. */
class ArabicNoun(val one: String, val two: String, val few: String, val many: String) {
    /** مثل "٣ أيام" / "يوم واحد" / "يومين" / "١٢ يومًا". [indic] يحوّل الأرقام إلى هندية. */
    fun count(n: Int, indic: (Int) -> String = { it.toString() }): String = when {
        n == 1 -> one
        n == 2 -> two
        n in 3..10 -> "${indic(n)} $few"
        n <= 0 -> "لا شيء"
        else -> "${indic(n)} $many"
    }

    companion object {
        val DAY = ArabicNoun("يوم واحد", "يومين", "أيام", "يومًا")
        val RAKAA = ArabicNoun("ركعة واحدة", "ركعتين", "ركعات", "ركعة")
        val PRAYER = ArabicNoun("صلاة واحدة", "صلاتين", "صلوات", "صلاة")
        val TASK = ArabicNoun("مهمة واحدة", "مهمتين", "مهام", "مهمة")
        val PAGE = ArabicNoun("صفحة واحدة", "صفحتين", "صفحات", "صفحة")
        val AYAH = ArabicNoun("آية واحدة", "آيتين", "آيات", "آية")
        val ITEM = ArabicNoun("ذكر واحد", "ذكرين", "أذكار", "ذكرًا")
        val WEEK = ArabicNoun("أسبوع واحد", "أسبوعين", "أسابيع", "أسبوعًا")
    }
}

// ============================================================ التتابع (Streak)

data class Streak(val current: Int, val best: Int, val bestStart: LocalDate?, val bestEnd: LocalDate?)

/**
 * تعريف التتابع (موثَّق): "يوم نشط" = يوم فيه أي نشاط عبادي مسجَّل ([hasWorshipActivity]): صلاة مؤداة أو سنة أو ذكر مكتمل
 * أو قراءة قرآن (المهام وحدها لا تكفي، وتسجيل "لم أصلِّ بعد" وحده لا يكفي).
 *  - التتابع الحالي = عدد الأيام النشطة المتصلة المنتهية بـ"اليوم"، وإن لم يُسجَّل اليوم شيء بعد فيُحسب المنتهي بالأمس
 *    (لا ينقطع التتابع قبل انتهاء اليوم).
 *  - أفضل تتابع = أطول سلسلة أيام نشطة متصلة في البيانات المتاحة (عند التساوي تُفضَّل الأحدث).
 * غير المسجّل يقطع السلسلة حسابيًا فقط، ولا يُعرض في أي مكان على أنه تقصير.
 */
object StreakCalculator {
    fun activeDays(summaries: List<DailySummaryEntity>): Set<LocalDate> =
        summaries.filter { it.hasWorshipActivity() }.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()

    fun compute(activeDays: Set<LocalDate>, today: LocalDate): Streak {
        val days = activeDays.filter { !it.isAfter(today) }.toSortedSet()
        if (days.isEmpty()) return Streak(0, 0, null, null)

        var best = 0
        var bestStart: LocalDate? = null
        var bestEnd: LocalDate? = null
        var runStart: LocalDate? = null
        var prev: LocalDate? = null
        var runLen = 0
        for (d in days) {
            if (prev != null && prev.plusDays(1) == d) {
                runLen++
            } else {
                runStart = d
                runLen = 1
            }
            if (runLen >= best) { // >= : عند التساوي الأحدث
                best = runLen
                bestStart = runStart
                bestEnd = d
            }
            prev = d
        }

        val anchor = when {
            today in days -> today
            today.minusDays(1) in days -> today.minusDays(1)
            else -> null
        }
        var current = 0
        if (anchor != null) {
            var d: LocalDate = anchor
            while (d in days) { current++; d = d.minusDays(1) }
        }
        return Streak(current, best, bestStart, bestEnd)
    }
}

// ============================================================ الخريطة الحرارية

/** خلية في الخريطة: level 0 = غير مسجّل، 1..4 درجات. */
data class HeatCell(val date: LocalDate, val level: Int, val units: Int)

/** أعمدة = أسابيع (الأقدم أولًا)، وكل أسبوع 7 خلايا من السبت إلى الجمعة؛ null = يوم مستقبلي. */
data class HeatmapGrid(val weeks: List<List<HeatCell?>>) {
    val firstDate: LocalDate? get() = weeks.firstOrNull()?.firstOrNull()?.date
}

object HeatmapBuilder {
    /** درجة اللون حسب نسبة ما سُجّل من الحد الأقصى اليومي. أي نشاط = درجة 1 على الأقل. */
    fun level(s: DailySummaryEntity?): Int {
        if (s == null || !s.hasWorshipActivity()) return 0
        val units = s.worshipUnits()
        val ratio = units.toDouble() / JourneyConfig.MAX_DAILY_UNITS
        return when {
            ratio <= 0.0 -> 1
            ratio <= 0.25 -> 1
            ratio <= 0.5 -> 2
            ratio <= 0.75 -> 3
            else -> 4
        }
    }

    /** السبت = أول الأسبوع. */
    fun startOfWeek(d: LocalDate): LocalDate {
        val back = (d.dayOfWeek.value - DayOfWeek.SATURDAY.value + 7) % 7
        return d.minusDays(back.toLong())
    }

    fun build(summaries: List<DailySummaryEntity>, today: LocalDate, weeks: Int = JourneyConfig.HEATMAP_WEEKS): HeatmapGrid {
        val byDate = summaries.filter { !it.deleted }.associateBy { it.date }
        val firstWeekStart = startOfWeek(today).minusWeeks((weeks - 1).toLong())
        val grid = (0 until weeks).map { w ->
            (0 until 7).map { dow ->
                val d = firstWeekStart.plusDays((w * 7 + dow).toLong())
                if (d.isAfter(today)) null else {
                    val s = byDate[d.toString()]
                    HeatCell(d, level(s), s?.worshipUnits() ?: 0)
                }
            }
        }
        return HeatmapGrid(grid)
    }
}

// ============================================================ إحصاءات الفترة

data class QuranStats(
    val daysRead: Int,
    val pages: Int,
    val ayahs: Int,
    /** ما يعادل الصفحات بالأجزاء (604 صفحة ÷ 30 جزءًا). */
    val juzEquivalent: Double,
)

/** التزام الورد: أيام تحقق فيها المقدار ÷ أيام وُجد فيها سجل لورد نشط. المقام صريح. */
data class WirdCommitment(val daysWithLog: Int, val daysTargetMet: Int) {
    val ratio: Double? get() = if (daysWithLog == 0) null else daysTargetMet.toDouble() / daysWithLog
}

data class PlansOverview(val active: Int, val paused: Int, val completed: Int, val activeRemainingPages: Int?)

data class AdhkarStats(
    val morningDays: Int,
    val eveningDays: Int,
    val sleepDays: Int,
    val itemsCompleted: Int,
    /** أيام سُجّل فيها ذكر واحد على الأقل (مقام النسب). */
    val recordedDays: Int,
) {
    fun rate(days: Int): Double? = if (recordedDays == 0) null else days.toDouble() / recordedDays
}

data class PrayerStatsSummary(
    val prayed: Int,
    val mosque: Int,
    val home: Int,
    /** عدد من سُجّلت لهم "لم أصلِّ بعد" صراحةً. */
    val markedNotYet: Int,
    val daysRecorded: Int,
    /** عدد خانات الصلاة التي لم تُسجَّل في أيام مكتملة منذ بداية استعمالك للتسجيل (لا تعني أنها لم تُؤدَّ). */
    val unrecordedSlots: Int,
) {
    val mosqueShare: Double? get() = if (prayed == 0) null else mosque.toDouble() / prayed
    val homeShare: Double? get() = if (prayed == 0) null else home.toDouble() / prayed
}

data class RawatibStats(val rakaat: Int, val daysDone: Int, val activeDays: Int) {
    val consistency: Double? get() = if (activeDays == 0) null else daysDone.toDouble() / activeDays
}

data class TaskBest(val from: LocalDate, val to: LocalDate, val done: Int, val total: Int) {
    val ratio: Double get() = if (total == 0) 0.0 else done.toDouble() / total
    val days: Int get() = (ChronoUnit.DAYS.between(from, to) + 1).toInt()
}

data class TasksStats(
    val total: Int,
    val done: Int,
    /** أيام فيها نشاط (المقام: لا تُحسب أيام لم تفتح فيها شيئًا). */
    val daysCounted: Int,
    val bestWindow: TaskBest?,
    val bestDay: TaskBest?,
) {
    val completion: Double? get() = if (total == 0) null else done.toDouble() / total
}

object PeriodAggregator {
    private fun inRange(summaries: List<DailySummaryEntity>, range: PeriodRange): List<DailySummaryEntity> =
        summaries.filter { s -> !s.deleted && runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { it in range } == true }
            .distinctBy { it.date }

    fun quran(summaries: List<DailySummaryEntity>, range: PeriodRange, totalPages: Int = 604): QuranStats {
        val rows = inRange(summaries, range)
        val read = rows.filter { it.quranPages > 0 || it.quranAyahs > 0 }
        val pages = read.sumOf { it.quranPages }
        return QuranStats(read.size, pages, read.sumOf { it.quranAyahs }, pages / (totalPages / 30.0))
    }

    /**
     * التزام الورد من سجلات الأوراد النشطة مباشرةً. يوم "فيه سجل ورد" = أي يوم له سجل لورد من [activePlanIds].
     * اليوم "محقَّق" إن بلغت كل سجلاته مقدارها.
     */
    fun wird(logs: List<QuranPlanLogEntity>, activePlanIds: Set<String>, range: PeriodRange): WirdCommitment {
        val byDate = logs.filter { !it.deleted && it.planId in activePlanIds }
            .filter { runCatching { LocalDate.parse(it.date) }.getOrNull()?.let { d -> d in range } == true }
            .groupBy { it.date }
        return WirdCommitment(byDate.size, byDate.count { (_, l) -> l.all { it.targetMet } })
    }

    fun plans(plans: List<QuranPlanEntity>, pageOf: ((Int) -> Int)?): PlansOverview {
        val live = plans.filter { !it.deleted }
        val active = live.filter { it.status == QuranPlanStatus.ACTIVE.name }
        val remaining = if (active.isEmpty() || pageOf == null) null else active.sumOf { remainingPages(it, pageOf) }
        return PlansOverview(
            active = active.size,
            paused = live.count { it.status == QuranPlanStatus.PAUSED.name },
            completed = live.count { it.status == QuranPlanStatus.COMPLETED.name },
            activeRemainingPages = remaining,
        )
    }

    /** الصفحات المتبقية في ورد: من الآية التالية لآخر ما أُنجز حتى آخر آية في الخطة. 0 إن اكتمل. */
    fun remainingPages(plan: QuranPlanEntity, pageOf: (Int) -> Int): Int {
        val next = (plan.progressAyahId + 1).coerceAtLeast(plan.startAyahId)
        if (next > plan.endAyahId) return 0
        return (pageOf(plan.endAyahId) - pageOf(next) + 1).coerceAtLeast(0)
    }

    fun adhkar(summaries: List<DailySummaryEntity>, range: PeriodRange): AdhkarStats {
        val rows = inRange(summaries, range)
        return AdhkarStats(
            morningDays = rows.count { it.adhkarMorningDone },
            eveningDays = rows.count { it.adhkarEveningDone },
            sleepDays = rows.count { it.adhkarSleepDone },
            itemsCompleted = rows.sumOf { it.adhkarItemsCompleted },
            recordedDays = rows.count { it.adhkarItemsCompleted > 0 },
        )
    }

    /**
     * @param trackingStart أول يوم ظهر فيه أي نشاط مسجّل (من كل الملخصات)؛ منه تبدأ حساب "الخانات غير المسجّلة".
     * @param today اليوم؛ لا تُحسب خانات اليوم نفسه لأنه لم ينتهِ.
     */
    fun prayers(summaries: List<DailySummaryEntity>, range: PeriodRange, trackingStart: LocalDate?, today: LocalDate): PrayerStatsSummary {
        val rows = inRange(summaries, range)
        val mosque = rows.sumOf { it.prayersMosque }
        val home = rows.sumOf { it.prayersHome }
        val notYet = rows.sumOf { it.prayersNotPrayed }

        var unrecorded = 0
        if (trackingStart != null) {
            val start = maxOf(range.from, trackingStart)
            val end = minOf(range.to, today.minusDays(1))
            if (!end.isBefore(start)) {
                val complete = PeriodRange(start, end)
                val recorded = inRange(summaries, complete).sumOf { it.prayersRecorded }
                unrecorded = (complete.days * JourneyConfig.PRAYERS_PER_DAY - recorded).coerceAtLeast(0)
            }
        }
        return PrayerStatsSummary(
            prayed = mosque + home,
            mosque = mosque,
            home = home,
            markedNotYet = notYet,
            daysRecorded = rows.count { it.prayersRecorded > 0 },
            unrecordedSlots = unrecorded,
        )
    }

    fun rawatib(summaries: List<DailySummaryEntity>, range: PeriodRange): RawatibStats {
        val rows = inRange(summaries, range)
        return RawatibStats(
            rakaat = rows.sumOf { it.rawatibRakaat },
            daysDone = rows.count { it.rawatibDone > 0 },
            activeDays = rows.count { it.hasWorshipActivity() },
        )
    }

    fun tasks(summaries: List<DailySummaryEntity>, range: PeriodRange, window: Int = 7): TasksStats {
        val rows = inRange(summaries, range).filter { it.tasksTotal > 0 }.sortedBy { it.date }
        val total = rows.sumOf { it.tasksTotal }
        val done = rows.sumOf { it.tasksDone.coerceAtMost(it.tasksTotal) }
        val bestDay = rows.map { TaskBest(LocalDate.parse(it.date), LocalDate.parse(it.date), it.tasksDone.coerceAtMost(it.tasksTotal), it.tasksTotal) }
            .maxWithOrNull(compareBy<TaskBest> { it.ratio }.thenBy { it.done }.thenBy { it.to })
        return TasksStats(
            total = total,
            done = done,
            daysCounted = rows.size,
            bestWindow = if (range.days > window) BestPeriodFinder.bestWindow(rows, window) else null,
            bestDay = bestDay,
        )
    }

    /** أول يوم فيه أي قيمة ذات معنى. */
    fun trackingStart(summaries: List<DailySummaryEntity>): LocalDate? =
        summaries.filter { !it.deleted && it.isMeaningful() }.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.minOrNull()
}

/** أفضل فترة متتالية للمهام. */
object BestPeriodFinder {
    /**
     * أفضل نافذة من [windowDays] يومًا تقويميًا متتاليًا حسب نسبة الإنجاز (مجموع المنجز ÷ مجموع المطلوب)،
     * بشرط أن تحوي النافذة 3 أيام عليها مهام على الأقل (وإلا فهي نافذة ضعيفة الدلالة).
     * عند التساوي: الأكثر إنجازًا ثم الأحدث.
     * @param rows ملخصات الأيام التي فيها مهام (tasksTotal > 0) مرتبة تصاعديًا.
     */
    fun bestWindow(rows: List<DailySummaryEntity>, windowDays: Int, minDaysWithTasks: Int = 3): TaskBest? {
        if (rows.isEmpty()) return null
        val dated = rows.map { LocalDate.parse(it.date) to it }
        var best: TaskBest? = null
        for ((end, _) in dated) {
            val start = end.minusDays((windowDays - 1).toLong())
            val inWin = dated.filter { (d, _) -> !d.isBefore(start) && !d.isAfter(end) }
            if (inWin.size < minDaysWithTasks) continue
            val total = inWin.sumOf { it.second.tasksTotal }
            val done = inWin.sumOf { it.second.tasksDone.coerceAtMost(it.second.tasksTotal) }
            val cand = TaskBest(inWin.first().first, inWin.last().first, done, total)
            val cur = best
            if (cur == null || better(cand, cur)) best = cand
        }
        return best
    }

    private fun better(a: TaskBest, b: TaskBest): Boolean = when {
        a.ratio != b.ratio -> a.ratio > b.ratio
        a.done != b.done -> a.done > b.done
        else -> a.to.isAfter(b.to) || a.to == b.to
    }
}

// ============================================================ المقارنة بين فترتين

enum class CompareMetric(val label: String, val noun: ArabicNoun) {
    QURAN_DAYS("أيام القراءة", ArabicNoun.DAY),
    PRAYERS("الصلوات المؤداة المسجّلة", ArabicNoun.PRAYER),
    MORNING("أيام إتمام أذكار الصباح", ArabicNoun.DAY),
    EVENING("أيام إتمام أذكار المساء", ArabicNoun.DAY),
    RAWATIB("ركعات السنن", ArabicNoun.RAKAA),
    TASKS("المهام المنجزة", ArabicNoun.TASK),
}

enum class Trend { UP, DOWN, SAME }

data class MetricComparison(val metric: CompareMetric, val current: Int, val previous: Int) {
    val diff: Int get() = current - previous
    val trend: Trend get() = when { diff > 0 -> Trend.UP; diff < 0 -> Trend.DOWN; else -> Trend.SAME }

    /** عبارة هادئة محايدة: لا لوم عند النقصان. */
    fun phrase(indic: (Int) -> String = { it.toString() }): String = when (trend) {
        Trend.UP -> "زاد عن الفترة السابقة بـ ${metric.noun.count(diff, indic)}"
        Trend.SAME -> "مماثل للفترة السابقة"
        Trend.DOWN -> "أقل بـ ${metric.noun.count(-diff, indic)} من الفترة السابقة، ولكل فترة ظروفها"
    }
}

data class PeriodComparison(val hasPrevious: Boolean, val metrics: List<MetricComparison>)

object PeriodComparer {
    fun compare(summaries: List<DailySummaryEntity>, current: PeriodRange): PeriodComparison {
        val prev = current.previous()
        val prevHasData = summaries.any { s ->
            !s.deleted && s.isMeaningful() && runCatching { LocalDate.parse(s.date) }.getOrNull()?.let { it in prev } == true
        }
        fun metrics(r: PeriodRange): Map<CompareMetric, Int> {
            val q = PeriodAggregator.quran(summaries, r)
            val a = PeriodAggregator.adhkar(summaries, r)
            val p = PeriodAggregator.prayers(summaries, r, null, r.to)
            val ra = PeriodAggregator.rawatib(summaries, r)
            val t = PeriodAggregator.tasks(summaries, r)
            return mapOf(
                CompareMetric.QURAN_DAYS to q.daysRead,
                CompareMetric.PRAYERS to p.prayed,
                CompareMetric.MORNING to a.morningDays,
                CompareMetric.EVENING to a.eveningDays,
                CompareMetric.RAWATIB to ra.rakaat,
                CompareMetric.TASKS to t.done,
            )
        }
        val cur = metrics(current)
        val old = metrics(prev)
        return PeriodComparison(
            hasPrevious = prevHasData,
            metrics = CompareMetric.entries.map { MetricComparison(it, cur.getValue(it), old.getValue(it)) },
        )
    }
}

// ============================================================ بيانات الرسوم

/** نقطة في رسم يومي: [units] = null تعني "غير مسجّل" (لا صفر). */
data class DayPoint(val date: LocalDate, val units: Int?)

object ChartBuilder {
    /** آخر [days] أيام تنتهي باليوم، الأقدم أولًا. اليوم غير المسجّل = units null. */
    fun daily(summaries: List<DailySummaryEntity>, today: LocalDate, days: Int): List<DayPoint> {
        val byDate = summaries.filter { !it.deleted }.associateBy { it.date }
        return (days - 1 downTo 0).map { back ->
            val d = today.minusDays(back.toLong())
            val s = byDate[d.toString()]
            DayPoint(d, if (s != null && s.hasWorshipActivity()) s.worshipUnits() else null)
        }
    }
}

// ============================================================ عبارات التشجيع

/**
 * عبارات متوازنة وهادئة. لا تصف المستخدم بالتقصير ولا بالالتزام من رقم واحد، ولا تقارنه بغيره.
 * الاختيار حتمي حسب [seed] (اليوم) فلا تتغير العبارة عند كل إعادة رسم.
 */
object Encouragement {
    fun forJourney(activeDaysInPeriod: Int, periodDays: Int, currentStreak: Int, hasAnyData: Boolean, seed: Long): String {
        val pool: List<String> = when {
            !hasAnyData -> listOf(
                "رحلتك تبدأ من حيث أنت، وأول تسجيل هو أول خطوة.",
                "سجّل ما تفعله بهدوء، وستظهر لك صورة رحلتك هنا.",
            )
            activeDaysInPeriod == 0 -> listOf(
                "لا شيء مسجّل في هذه الفترة، وهذا لا يعني أنك لم تفعل شيئًا. يمكنك البدء بتسجيل ما تؤديه متى شئت.",
                "غير المسجّل لا يُحسب عليك. عُد حين تحب، فباب البداية مفتوح دائمًا.",
            )
            currentStreak >= 3 -> listOf(
                "ثباتٌ جميل في الأيام الأخيرة. بارك الله فيك، واستمر على ما يناسبك.",
                "أيامٌ متصلة من الذكر والعمل، فلتكن خفيفةً عليك دائمًا.",
            )
            activeDaysInPeriod * 2 >= periodDays -> listOf(
                "حضورٌ متكرر في هذه الفترة. أحسن الله إليك، وأدام عليك الخير.",
                "ما سجّلتَه يدل على اهتمام صادق، فواصل على مهلك.",
            )
            else -> listOf(
                "كل يوم تسجّل فيه شيئًا هو خطوة تُحسب لك. خذ وقتك ولا تستعجل.",
                "القليل الدائم خير من الكثير المنقطع. اختر ما يناسب أيامك.",
                "رحلتك خاصة بك، تتقدم بالوتيرة التي تناسبك.",
            )
        }
        return pool[Math.floorMod(seed, pool.size.toLong()).toInt()]
    }
}
