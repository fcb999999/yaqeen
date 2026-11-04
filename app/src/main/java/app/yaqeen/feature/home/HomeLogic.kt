package app.yaqeen.feature.home

import app.yaqeen.core.ArabicText
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.domain.model.AdhkarCategoryId
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.QuranPlanUnit
import app.yaqeen.domain.model.Sunnah
import app.yaqeen.feature.journey.AdhkarItemIndex
import app.yaqeen.feature.journey.ArabicNoun
import app.yaqeen.feature.journey.JourneyFormat
import java.time.Duration
import java.time.ZonedDateTime

/**
 * منطق الرئيسية الخالص (بلا Android/Compose) ليسهل اختباره.
 */

// ============================================================ أقسام اللوحة وتخصيصها

object HomeSections {
    const val OVERALL = "overall"
    const val NEXT_PRAYER = "next_prayer"
    const val PRAYERS = "prayers"
    const val WIRD = "wird"
    const val CONTINUE_READING = "continue_reading"
    const val ADHKAR = "adhkar"
    const val RAWATIB = "rawatib"
    const val TASKS = "tasks"
    const val EVENT = "event"

    /** القيمة المخزَّنة حين يخفي المستخدم كل الأقسام (فارغ يعني الافتراضي). */
    const val NONE = "none"

    val DEFAULT_ORDER = listOf(OVERALL, NEXT_PRAYER, PRAYERS, WIRD, CONTINUE_READING, ADHKAR, RAWATIB, TASKS, EVENT)

    fun title(key: String): String = when (key) {
        OVERALL -> "إنجاز اليوم"
        NEXT_PRAYER -> "الصلاة القادمة"
        PRAYERS -> "صلوات اليوم"
        WIRD -> "الورد القرآني"
        CONTINUE_READING -> "أكمل القراءة"
        ADHKAR -> "أذكار اليوم"
        RAWATIB -> "السنن الرواتب"
        TASKS -> "مهام اليوم"
        EVENT -> "المناسبة القادمة"
        else -> key
    }

    data class Entry(val key: String, val visible: Boolean)

    /** الأقسام المرئية بالترتيب من القيمة المخزَّنة. فارغ = الافتراضي، "none" = لا شيء. المفاتيح المجهولة تُهمل. */
    fun visible(csv: String): List<String> {
        val t = csv.trim()
        if (t.isEmpty()) return DEFAULT_ORDER
        if (t == NONE) return emptyList()
        val parsed = t.split(',').map { it.trim() }.filter { it in DEFAULT_ORDER }.distinct()
        return parsed.ifEmpty { DEFAULT_ORDER }
    }

    /** كل الأقسام: المرئية بترتيبها أولًا ثم المخفية بالترتيب الافتراضي (لواجهة التخصيص). */
    fun layout(csv: String): List<Entry> {
        val vis = visible(csv)
        return vis.map { Entry(it, true) } + DEFAULT_ORDER.filter { it !in vis }.map { Entry(it, false) }
    }

    /** يحوّل التخطيط إلى القيمة المخزّنة. الافتراضي الكامل يُخزَّن فارغًا. */
    fun encode(layout: List<Entry>): String {
        val vis = layout.filter { it.visible }.map { it.key }
        return when {
            vis.isEmpty() -> NONE
            vis == DEFAULT_ORDER -> ""
            else -> vis.joinToString(",")
        }
    }

    fun toggle(csv: String, key: String): String =
        encode(layout(csv).map { if (it.key == key) it.copy(visible = !it.visible) else it })

    /** نقل قسم مرئي خطوة لأعلى (-1) أو لأسفل (+1) ضمن المرئيين. */
    fun move(csv: String, key: String, delta: Int): String {
        val l = layout(csv).toMutableList()
        val from = l.indexOfFirst { it.key == key }
        if (from < 0 || !l[from].visible) return csv
        val to = from + delta
        if (to < 0 || to >= l.size || !l[to].visible) return csv
        val item = l.removeAt(from)
        l.add(to, item)
        return encode(l)
    }
}

// ============================================================ التحية والعرض

object HomeText {
    /** تحية محايدة حسب الساعة (0..23). */
    fun greeting(hour: Int): String = when (hour) {
        in 4..11 -> "صباح الخير"
        in 12..14 -> "طاب يومك"
        in 15..23 -> "مساء الخير"
        else -> "طاب ليلك"
    }

    /** "٠٣:٤٥ م" */
    fun clock12(t: ZonedDateTime): String {
        val h = t.hour % 12
        val h12 = if (h == 0) 12 else h
        val ampm = if (t.hour < 12) "ص" else "م"
        return ArabicText.indic("%02d:%02d".format(h12, t.minute)) + " " + ampm
    }

    /** مدة متبقية بصيغة ٠٢:١٤:٣٠ . الصفر أو السالب = ٠٠:٠٠:٠٠ */
    fun countdown(remaining: Duration): String {
        val total = remaining.seconds.coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return ArabicText.indic("%02d:%02d:%02d".format(h, m, s))
    }

    /** وصف قصير للمدة المتبقية للقراءة الصوتية: "بعد ساعتين و١٠ دقائق". */
    fun spokenRemaining(remaining: Duration): String {
        val total = remaining.seconds.coerceAtLeast(0)
        val h = (total / 3600).toInt()
        val m = ((total % 3600) / 60).toInt()
        val hours = ArabicNoun("ساعة واحدة", "ساعتين", "ساعات", "ساعة").count(h) { ArabicText.indic(it) }
        val mins = ArabicNoun("دقيقة واحدة", "دقيقتين", "دقائق", "دقيقة").count(m) { ArabicText.indic(it) }
        return when {
            h > 0 && m > 0 -> "بعد $hours و$mins"
            h > 0 -> "بعد $hours"
            m > 0 -> "بعد $mins"
            else -> "بعد أقل من دقيقة"
        }
    }

    /** "اليوم" / "غدًا" / "بعد يومين" / "بعد ٥ أيام" / "بعد ١٢ يومًا" */
    fun daysUntil(days: Int): String = when {
        days <= 0 -> "اليوم"
        days == 1 -> "غدًا"
        else -> "بعد " + ArabicNoun.DAY.count(days) { ArabicText.indic(it) }
    }

    /** سطر تحفيزي هادئ بحسب نسبة إنجاز اليوم. لا لوم أبدًا. الاختيار حتمي حسب [seed]. */
    fun motivation(ratio: Float, seed: Long): String {
        val pool = when {
            ratio <= 0f -> listOf(
                "يومٌ جديد، وكل خطوة صغيرة لها قيمتها.",
                "ابدأ بما تيسّر، فبداية هادئة خير من لا بداية.",
            )
            ratio < 0.5f -> listOf(
                "بداية طيبة، واصل على مهلك.",
                "خطوات صغيرة متصلة، وهذا يكفي اليوم.",
            )
            ratio < 1f -> listOf(
                "أحسنت، وما بقي يسير بإذن الله.",
                "أنت في منتصف الطريق أو أبعد، فواصل بهدوء.",
            )
            else -> listOf(
                "أتممتَ ما خططتَ له اليوم، تقبّل الله منك.",
                "يوم مكتمل بحمد الله، فخذ قسطًا من الراحة.",
            )
        }
        return pool[Math.floorMod(seed, pool.size.toLong()).toInt()]
    }
}

// ============================================================ حالة الصلوات

data class PrayerChip(val prayer: Prayer, val status: PrayerStatus?, val row: PrayerLogEntity?)

object PrayersToday {
    fun chips(rows: List<PrayerLogEntity>): List<PrayerChip> {
        val byPrayer = rows.filter { !it.deleted }.groupBy { it.prayer }.mapValues { (_, r) -> r.maxByOrNull { it.updatedAt }!! }
        return Prayer.entries.map { p ->
            val row = byPrayer[p.name]
            PrayerChip(p, row?.let { r -> PrayerStatus.entries.firstOrNull { it.name == r.status } }, row)
        }
    }

    /** المؤداة فقط (مسجد/بيت). "لم أصلِّ بعد" وغير المسجّلة ليست مؤداة. */
    fun prayed(chips: List<PrayerChip>): Int = chips.count { it.status == PrayerStatus.MOSQUE || it.status == PrayerStatus.HOME }

    fun id(date: String, prayer: Prayer) = "${date}_${prayer.name}"
}

// ============================================================ السنن

data class RawatibToday(val doneDefaults: Int, val totalDefaults: Int, val rakaat: Int, val customDone: Int)

object RawatibSummary {
    fun of(rows: List<RawatibLogEntity>): RawatibToday {
        val done = rows.filter { !it.deleted && it.done }.groupBy { it.sunnahId }.mapValues { (_, r) -> r.maxByOrNull { it.updatedAt }!! }
        val defaults = done.keys.count { id -> Sunnah.entries.any { it.name == id } }
        val custom = done.keys.count { it.startsWith(Sunnah.CUSTOM_PREFIX) }
        val rakaat = done.values.sumOf { row ->
            if (row.rakaat > 0) row.rakaat else Sunnah.entries.firstOrNull { it.name == row.sunnahId }?.rakaat ?: 0
        }
        return RawatibToday(defaults, Sunnah.entries.size, rakaat, custom)
    }
}

// ============================================================ الأذكار

data class AdhkarStatusRow(val categoryId: String, val title: String, val done: Int, val total: Int) {
    val complete: Boolean get() = total > 0 && done >= total
    val started: Boolean get() = done > 0
    val ratio: Float get() = if (total == 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

object AdhkarToday {
    private val CATEGORIES = listOf(
        AdhkarCategoryId.MORNING to "أذكار الصباح",
        AdhkarCategoryId.EVENING to "أذكار المساء",
        AdhkarCategoryId.SLEEP to "أذكار النوم",
    )

    /** done = عدد أذكار القسم المكتملة اليوم (ضمن عناصر الكتالوج فقط)، total = كل أذكار القسم. */
    fun rows(index: AdhkarItemIndex, progress: List<AdhkarProgressEntity>): List<AdhkarStatusRow> {
        val completed = progress.filter { !it.deleted && (it.completed || (it.target > 0 && it.count >= it.target)) }
            .groupBy { it.categoryId }.mapValues { (_, r) -> r.map { it.itemId }.toSet() }
        return CATEGORIES.map { (id, title) ->
            val all = index.itemsByCategory[id].orEmpty()
            AdhkarStatusRow(id, title, completed[id].orEmpty().count { it in all }, all.size)
        }
    }
}

// ============================================================ الورد

data class WirdTodayItem(
    val plan: QuranPlanEntity,
    /** مثل: "٣ من ٥ صفحات". */
    val todayLabel: String,
    val ratio: Float,
    val targetMet: Boolean,
    /** الصفحات المتبقية في الخطة، أو null إن لم يُعرف. */
    val remainingPages: Int?,
)

object WirdToday {
    private val JUZ = ArabicNoun("جزء واحد", "جزأين", "أجزاء", "جزءًا")

    fun items(
        plans: List<QuranPlanEntity>,
        logs: List<QuranPlanLogEntity>,
        date: String,
        totalPages: Int,
        pageOf: ((Int) -> Int)?,
    ): List<WirdTodayItem> {
        val byPlan = logs.filter { !it.deleted && it.date == date }.groupBy { it.planId }.mapValues { (_, r) -> r.maxByOrNull { it.updatedAt }!! }
        return plans.filter { !it.deleted }.map { plan ->
            val log = byPlan[plan.id]
            val target = plan.dailyAmount.coerceAtLeast(1)
            val unit = QuranPlanUnit.entries.firstOrNull { it.name == plan.unit } ?: QuranPlanUnit.PAGE
            val label: String
            val current: Double
            when (unit) {
                QuranPlanUnit.PAGE -> {
                    current = (log?.pagesRead ?: 0).toDouble()
                    label = "${ArabicText.indic(current.toInt())} من ${ArabicNoun.PAGE.count(target) { ArabicText.indic(it) }}"
                }
                QuranPlanUnit.AYAH -> {
                    current = (log?.ayahsRead ?: 0).toDouble()
                    label = "${ArabicText.indic(current.toInt())} من ${ArabicNoun.AYAH.count(target) { ArabicText.indic(it) }}"
                }
                QuranPlanUnit.JUZ -> {
                    val pagesPerJuz = totalPages / 30.0
                    current = (log?.pagesRead ?: 0) / pagesPerJuz
                    label = "${JourneyFormat.decimal1(current)} من ${JUZ.count(target) { ArabicText.indic(it) }}"
                }
            }
            val met = log?.targetMet == true
            val ratio = if (met) 1f else (current / target).toFloat().coerceIn(0f, 1f)
            WirdTodayItem(plan, label, ratio, met, remainingPages(plan, pageOf))
        }
    }

    /** الصفحات المتبقية من الآية التالية لآخر ما أُنجز حتى نهاية الخطة. 0 إن اكتملت. */
    fun remainingPages(plan: QuranPlanEntity, pageOf: ((Int) -> Int)?): Int? {
        if (pageOf == null) return null
        val next = (plan.progressAyahId + 1).coerceAtLeast(plan.startAyahId)
        if (next > plan.endAyahId) return 0
        return (pageOf(plan.endAyahId) - pageOf(next) + 1).coerceAtLeast(0)
    }
}

// ============================================================ الإنجاز العام

data class OverallProgress(val done: Int, val total: Int) {
    val ratio: Float get() = if (total == 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

object OverallCompletion {
    /**
     * إنجاز اليوم من المتاح: صلوات (5) + سنن (5) + أقسام أذكار (3، إن عُرف الكتالوج) + أوراد نشطة (واحد لكل ورد)
     * + مهام يدوية فقط (المهام المرتبطة بعبادة تُحسب أصلًا من مصدرها فلا تُكرَّر).
     * لا يُحسب شيء غير موجود: لا أوراد نشطة = لا خانة ورد، لا مهام = لا خانة مهام.
     */
    fun compute(
        prayed: Int,
        rawatibDone: Int,
        adhkarRows: List<AdhkarStatusRow>,
        wird: List<WirdTodayItem>,
        manualTasksTotal: Int,
        manualTasksDone: Int,
    ): OverallProgress {
        val adhkarKnown = adhkarRows.filter { it.total > 0 }
        val total = Prayer.entries.size + Sunnah.entries.size + adhkarKnown.size + wird.size + manualTasksTotal
        val done = prayed.coerceIn(0, Prayer.entries.size) +
            rawatibDone.coerceIn(0, Sunnah.entries.size) +
            adhkarKnown.count { it.complete } +
            wird.count { it.targetMet } +
            manualTasksDone.coerceIn(0, manualTasksTotal)
        return OverallProgress(done, total)
    }
}
