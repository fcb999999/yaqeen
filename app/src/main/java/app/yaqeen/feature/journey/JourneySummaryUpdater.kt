package app.yaqeen.feature.journey

import app.yaqeen.AppContainer
import app.yaqeen.data.local.DailySummaryEntity
import app.yaqeen.data.local.TaskCompletionEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** نتيجة تمريرة تحديث الملخصات. */
data class SummaryRefreshResult(val written: Int, val deleted: Int)

/**
 * يحسب [DailySummaryEntity] (مجموعة achievement_statistics) لآخر [JourneyConfig.SUMMARY_DAYS] يومًا من السجلات الخام،
 * ولا يكتب إلا الصفوف التي تغيّرت قيمها فعلًا (مقارنة تتجاهل بيانات المزامنة) حتى لا تُولَّد عمليات مزامنة بلا داعٍ.
 *
 *  - السجلات الخام هي مصدر الحقيقة؛ الملخص مخزن مؤقت يُزامَن ليراه المستخدم على أجهزته كلها.
 *  - اليوم الذي لا قيمة فيه ولا سجل خام له لا يُنشأ له صف. وإن صار فارغًا بعد حذف سجلاته يُحذف صفه (tombstone).
 *  - تقدّم المهام يُسأل من محرك المهام فقط للأيام التي لها سجلات خام، ويُخزَّن مؤقتًا في الذاكرة ويُعاد سؤاله فقط
 *    إذا تغيّرت مدخلات اليوم أو تعريفات المهام، فلا نعيد حساب 180 يومًا مع كل تغيير.
 */
class JourneySummaryUpdater(private val c: AppContainer) {

    private data class TaskKey(val inputs: DayInputs, val completions: List<TaskCompletionEntity>, val taskSig: Long)

    private val taskCache = HashMap<LocalDate, Pair<TaskKey, TaskDayCount>>()

    /** تمريرة واحدة كاملة. تُرمي الاستثناء عند فشل قراءة أساسية (الكتالوج أو قاعدة البيانات). */
    suspend fun refreshOnce(): SummaryRefreshResult = GLOBAL_LOCK.withLock {
        val db = c.db
        val today = c.clock.today()
        val range = PeriodRange.last(today, JourneyConfig.SUMMARY_DAYS)
        val from = range.from.toString()
        val to = today.toString()

        val catalog = c.adhkar()
        val index = AdhkarItemIndex(catalog.categories.associate { cat -> cat.id to cat.items.map { it.id }.toSet() })

        val activePlanIds = db.quranPlans().active().map { it.id }.toSet()
        val prayers = db.prayerLogs().range(from, to).groupBy { it.date }
        val rawatib = db.rawatibLogs().range(from, to).groupBy { it.date }
        val adhkar = db.adhkarProgress().range(from, to).groupBy { it.date }
        val dayLogs = db.quranDayLogs().range(from, to).associateBy { it.date }
        val planLogs = db.quranPlanLogs().range(from, to).filter { it.planId in activePlanIds }.groupBy { it.date }
        val completions = db.taskCompletions().range(from, to).groupBy { it.date }
        val stored = db.dailySummaries().range(from, to).associateBy { it.date }
        val taskSig = db.tasks().active().fold(17L) { acc, t -> acc * 31 + t.id.hashCode() * 7L + t.updatedAt }

        val toSave = ArrayList<DailySummaryEntity>()
        val toDelete = ArrayList<DailySummaryEntity>()

        for (offset in 0 until range.days) {
            val date = today.minusDays(offset.toLong())
            val key = date.toString()
            val inputs = DayInputs(
                date = date,
                prayers = prayers[key].orEmpty(),
                rawatib = rawatib[key].orEmpty(),
                adhkar = adhkar[key].orEmpty(),
                quranDay = dayLogs[key],
                planLogs = planLogs[key].orEmpty(),
            )
            val dayCompletions = completions[key].orEmpty()
            val hasData = inputs.hasAnyRaw() || dayCompletions.isNotEmpty()
            val tasks = if (hasData) tasksFor(date, TaskKey(inputs, dayCompletions, taskSig)) else null

            val computed = DailySummaryCalculator.compute(inputs, index, tasks)
            val old = stored[key]
            if (computed.isMeaningful()) {
                if (old == null || !old.sameValuesAs(computed)) toSave += computed
            } else if (old != null) {
                toDelete += old
            }
        }

        if (toSave.isNotEmpty()) c.writer.saveAll(db.dailySummaries(), toSave)
        toDelete.forEach { c.writer.delete(db.dailySummaries(), it) }
        // أيام خرجت من النافذة لا حاجة لها في الذاكرة.
        taskCache.keys.removeAll { it.isBefore(range.from) }
        SummaryRefreshResult(toSave.size, toDelete.size)
    }

    private suspend fun tasksFor(date: LocalDate, key: TaskKey): TaskDayCount? {
        taskCache[date]?.let { (k, v) -> if (k == key) return v }
        val fresh = runCatching {
            withTimeoutOrNull(4_000) { c.taskEngine.observeProgress(date).first() }
        }.getOrNull()
        if (fresh == null) return taskCache[date]?.second // فشل مؤقت: استعمل القيمة السابقة إن وُجدت ولا تخزّن
        val value = TaskDayCount(fresh.total, fresh.done)
        taskCache[date] = key to value
        return value
    }

    /** مصادر الإشارة: أي تغيير في السجلات الخام أو تعريفات المهام أو الأوراد النشطة. */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun triggers(): Flow<Unit> = DayTicker.today(c.clock).flatMapLatest { today ->
        val from = today.minusDays((JourneyConfig.SUMMARY_DAYS - 1).toLong()).toString()
        val to = today.toString()
        val db = c.db
        merge(
            db.prayerLogs().observeRange(from, to).map { },
            db.rawatibLogs().observeRange(from, to).map { },
            db.adhkarProgress().observeRange(from, to).map { },
            db.quranDayLogs().observeRange(from, to).map { },
            db.quranPlanLogs().observeRange(from, to).map { },
            db.taskCompletions().observeRange(from, to).map { },
            db.quranPlans().observeActive().map { },
            db.tasks().observeActive().map { },
        )
    }

    /**
     * يعمل ما دام المستدعي حيًّا (يُلغى بإلغاء الكوروتين). التمريرة الأولى فورية ثم تُجمَّع التغييرات المتتالية.
     * [onPass] يُستدعى بعد كل تمريرة (Throwable? = null عند النجاح).
     */
    @OptIn(FlowPreview::class)
    suspend fun runForever(onPass: (Throwable?) -> Unit) {
        var first = true
        triggers().debounce {
            if (first) { first = false; 0L } else DEBOUNCE_MS
        }.collect {
            val error = try {
                refreshOnce(); null
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                e
            }
            onPass(error)
        }
    }

    companion object {
        private const val DEBOUNCE_MS = 1_200L
        private val GLOBAL_LOCK = Mutex()
    }
}
