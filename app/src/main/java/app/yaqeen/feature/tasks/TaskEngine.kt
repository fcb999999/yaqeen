package app.yaqeen.feature.tasks

import app.yaqeen.AppContainer
import app.yaqeen.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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
    /** مهام يوم معيّن (بعد تطبيق التكرار وأيام الأسبوع) مع حالة الإنجاز. */
    fun observeDay(date: LocalDate): Flow<List<TodayTask>> = flowOf(emptyList())

    fun observeProgress(date: LocalDate): Flow<DayProgress> = flowOf(DayProgress(0, 0))
}
