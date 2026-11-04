package app.yaqeen.feature.journey

import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.DailySummaryEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranDayLogEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.domain.model.AdhkarCategoryId
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import java.time.LocalDate

/** ثوابت "رحلتي". */
object JourneyConfig {
    /**
     * عدد الأيام التي تُحسب لها الملخصات اليومية. 180 = 90 يومًا للفترة الأطول + 90 للفترة السابقة
     * التي تُقارَن بها.
     */
    const val SUMMARY_DAYS = 180
    const val HEATMAP_WEEKS = 13
    const val PRAYERS_PER_DAY = 5
    const val RAWATIB_PER_DAY = 5

    /** أقصى عدد "عبادات مسجّلة" في اليوم: صلوات 5 + سنن 5 + أقسام أذكار 3 + قراءة 1. */
    const val MAX_DAILY_UNITS = 14
    val PERIODS = listOf(7, 30, 90)
}

/** المدخلات الخام ليوم واحد (كلها مأخوذة من الجداول الأصلية). */
data class DayInputs(
    val date: LocalDate,
    val prayers: List<PrayerLogEntity> = emptyList(),
    val rawatib: List<RawatibLogEntity> = emptyList(),
    val adhkar: List<AdhkarProgressEntity> = emptyList(),
    val quranDay: QuranDayLogEntity? = null,
    /** سجلات الأوراد النشطة فقط (يُرشّحها المستدعي). */
    val planLogs: List<QuranPlanLogEntity> = emptyList(),
) {
    /** هل يوجد أي سجل خام لهذا اليوم؟ (يُستعمل لتخطي الأيام الفارغة قبل سؤال محرك المهام). */
    fun hasAnyRaw(): Boolean =
        prayers.any { !it.deleted } || rawatib.any { !it.deleted } || adhkar.any { !it.deleted } ||
            (quranDay != null && !quranDay.deleted) || planLogs.any { !it.deleted }
}

/** تقدّم المهام في يوم (من محرك المهام). */
data class TaskDayCount(val total: Int, val done: Int)

/** كتالوج الأذكار المختصر الذي تحتاجه الحسابات: معرّفات عناصر كل قسم. */
class AdhkarItemIndex(val itemsByCategory: Map<String, Set<String>>) {
    companion object {
        val EMPTY = AdhkarItemIndex(emptyMap())
    }
}

/**
 * يحسب [DailySummaryEntity] ليوم واحد من السجلات الخام. دالة خالصة (لا Android ولا I/O).
 *
 * القواعد:
 *  - الصفوف المحذوفة (deleted) تُهمَل.
 *  - صلاة واحدة لا تُحسب أكثر من مرة في اليوم (يغلب الأحدث تحديثًا إن تكرر الصف).
 *  - "prayersRecorded" = كل الصلوات التي سُجّلت لها حالة (مسجد + بيت + "لم أصلِّ بعد").
 *  - قسم الأذكار يُعدّ "تامًّا" فقط إذا اكتملت كل أذكاره في الكتالوج (غير المسجّل ليس تقصيرًا، لكنه ليس تامًّا).
 *  - ورد اليوم "محقَّق" إذا وُجد سجل ورد نشط واحد على الأقل وكلها بلغت مقدارها اليومي.
 */
object DailySummaryCalculator {

    fun compute(inputs: DayInputs, adhkar: AdhkarItemIndex, tasks: TaskDayCount?): DailySummaryEntity {
        val date = inputs.date.toString()

        // الصلوات: صف واحد لكل صلاة
        val prayerRows = inputs.prayers
            .filter { !it.deleted && it.date == date && Prayer.entries.any { p -> p.name == it.prayer } }
            .groupBy { it.prayer }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.updatedAt }!! }
            .values
        var mosque = 0
        var home = 0
        var notPrayed = 0
        for (row in prayerRows) {
            when (PrayerStatus.entries.firstOrNull { it.name == row.status }) {
                PrayerStatus.MOSQUE -> mosque++
                PrayerStatus.HOME -> home++
                PrayerStatus.NOT_PRAYED -> notPrayed++
                null -> Unit
            }
        }

        // السنن
        val rawatibRows = inputs.rawatib.filter { !it.deleted && it.date == date && it.done }
            .groupBy { it.sunnahId }
            .mapValues { (_, rows) -> rows.maxByOrNull { it.updatedAt }!! }
            .values
        val rawatibRakaat = rawatibRows.sumOf { it.rakaat.coerceAtLeast(0) }

        // الأذكار
        val adhkarRows = inputs.adhkar.filter { !it.deleted && it.date == date }
        val completedByCategory: Map<String, Set<String>> = adhkarRows
            .filter { it.completed || (it.target > 0 && it.count >= it.target) }
            .groupBy { it.categoryId }
            .mapValues { (_, rows) -> rows.map { it.itemId }.toSet() }
        fun kept(category: String): Boolean {
            val all = adhkar.itemsByCategory[category].orEmpty()
            if (all.isEmpty()) return false
            return completedByCategory[category].orEmpty().containsAll(all)
        }
        val itemsCompleted = completedByCategory.values.flatten().toSet().size

        // القرآن
        val dayLog = inputs.quranDay?.takeIf { !it.deleted && it.date == date }
        val activePlanLogs = inputs.planLogs.filter { !it.deleted && it.date == date }
        val pages = dayLog?.pagesRead ?: activePlanLogs.maxOfOrNull { it.pagesRead } ?: 0
        val ayahs = dayLog?.ayahsRead ?: activePlanLogs.maxOfOrNull { it.ayahsRead } ?: 0

        return DailySummaryEntity(
            id = date,
            date = date,
            prayersRecorded = mosque + home + notPrayed,
            prayersMosque = mosque,
            prayersHome = home,
            prayersNotPrayed = notPrayed,
            rawatibDone = rawatibRows.size,
            rawatibRakaat = rawatibRakaat,
            adhkarMorningDone = kept(AdhkarCategoryId.MORNING),
            adhkarEveningDone = kept(AdhkarCategoryId.EVENING),
            adhkarSleepDone = kept(AdhkarCategoryId.SLEEP),
            adhkarItemsCompleted = itemsCompleted,
            quranPages = pages.coerceAtLeast(0),
            quranAyahs = ayahs.coerceAtLeast(0),
            quranPlanTargetMet = activePlanLogs.isNotEmpty() && activePlanLogs.all { it.targetMet },
            tasksTotal = tasks?.total ?: 0,
            tasksDone = tasks?.done ?: 0,
        )
    }
}

// ----------------------------------------------------------------- امتدادات على الملخص

/** الصلوات المؤداة (مسجد + بيت). "لم أصلِّ بعد" ليست مؤداة، وغير المسجّلة لا تُحسب. */
val DailySummaryEntity.prayersPrayed: Int get() = prayersMosque + prayersHome

/** هل سُجّل في اليوم أي نشاط عبادي (المهام وحدها لا تُعدّ عبادة: قد تكون غير عبادية)؟ */
fun DailySummaryEntity.hasWorshipActivity(): Boolean =
    !deleted && (prayersPrayed > 0 || rawatibDone > 0 || adhkarItemsCompleted > 0 ||
        quranPages > 0 || quranAyahs > 0 || quranPlanTargetMet)

/** هل في الصف أي قيمة ذات معنى (تشمل إنجاز مهمة وتسجيل "لم أصلِّ بعد")؟ الصف الذي مجموع المهام فيه فقط لا يُخزَّن. */
fun DailySummaryEntity.isMeaningful(): Boolean =
    hasWorshipActivity() || prayersRecorded > 0 || tasksDone > 0

/** عدد العبادات المسجّلة في اليوم من أصل [JourneyConfig.MAX_DAILY_UNITS]. */
fun DailySummaryEntity.worshipUnits(): Int =
    minOf(prayersPrayed, JourneyConfig.PRAYERS_PER_DAY) +
        minOf(rawatibDone, JourneyConfig.RAWATIB_PER_DAY) +
        (if (adhkarMorningDone) 1 else 0) + (if (adhkarEveningDone) 1 else 0) + (if (adhkarSleepDone) 1 else 0) +
        (if (quranPages > 0 || quranAyahs > 0) 1 else 0)

/** مقارنة القيم متجاهلةً بيانات المزامنة (updatedAt, deleted, dirty, opId, deviceId). */
fun DailySummaryEntity.sameValuesAs(other: DailySummaryEntity): Boolean =
    copy(updatedAt = 0, deleted = false, dirty = false, opId = "", deviceId = "") ==
        other.copy(updatedAt = 0, deleted = false, dirty = false, opId = "", deviceId = "")
