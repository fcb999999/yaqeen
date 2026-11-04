package app.yaqeen.feature.tasks

import app.yaqeen.AppContainer
import app.yaqeen.core.dayKey
import app.yaqeen.data.adhkar.AdhkarCatalog
import app.yaqeen.data.local.TaskEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * === عقد محرك المهام (يملكه منفذ "المهام والتقويم") ===
 * الرئيسية وكشف الإحصاءات يقرآن حالة المهام من هنا فقط. المهمة المرتبطة بعبادة (task.link != NONE)
 * يُشتق إنجازها من سجل العبادة نفسه (لا علامة يدوية): [derived] = true.
 */
data class TodayTask(
    val task: TaskEntity,
    val done: Boolean,
    /** true إذا كان الإنجاز مشتقًا من سجل العبادة (صلاة/ورد/أذكار/سنن) ولا يمكن تعديله يدويًا. */
    val derived: Boolean,
    /** تفصيل للعرض، مثل "3 من 5 صلوات". */
    val detail: String? = null,
)

data class DayProgress(val total: Int, val done: Int) {
    val remaining: Int get() = (total - done).coerceAtLeast(0)
    val ratio: Float get() = if (total == 0) 0f else done.toFloat() / total
}

class TaskEngine(private val c: AppContainer) {
    /**
     * مهام يوم معيّن (بعد تطبيق التكرار وأيام الأسبوع) مع حالة الإنجاز.
     * تتحدّث فورًا عند أي تغيير في المهام أو الإنجازات اليدوية أو سجلات الصلاة/السنن/الأذكار/الورد.
     * الترتيب: ذات الوقت زمنيًا، ثم بلا وقت حسب الأولوية.
     */
    fun observeDay(date: LocalDate): Flow<List<TodayTask>> =
        combine(
            c.db.tasks().observeActive(),
            c.db.taskCompletions().observeDay(date.dayKey()),
            observeWorshipDay(c, date),
            catalogFlow(),
        ) { tasks, completions, worship, catalog ->
            val manual = completions.asSequence().filter { !it.deleted }.map { it.taskId }.toSet()
            val items = adhkarLookup(catalog)
            sortTodayTasks(tasks.filter { isTaskActiveOn(it, date) }.map { evaluateTask(it, manual, worship, items) })
        }.flowOn(Dispatchers.Default)

    fun observeProgress(date: LocalDate): Flow<DayProgress> =
        observeDay(date).map { list -> DayProgress(list.size, list.count { it.done }) }.distinctUntilChanged()

    /** لقطة واحدة (للتذكيرات والملخصات). */
    suspend fun dayOnce(date: LocalDate): List<TodayTask> = observeDay(date).first()

    private fun catalogFlow(): Flow<AdhkarCatalog?> = flow { emit(runCatching { c.adhkar() }.getOrNull()) }

    private fun adhkarLookup(catalog: AdhkarCatalog?): (String) -> List<String>? =
        { id -> catalog?.category(id)?.items?.map { it.id } }
}
