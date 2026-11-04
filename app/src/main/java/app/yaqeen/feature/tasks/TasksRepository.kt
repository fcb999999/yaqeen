package app.yaqeen.feature.tasks

import app.yaqeen.AppContainer
import app.yaqeen.core.dayKey
import app.yaqeen.core.newId
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.TaskCompletionEntity
import app.yaqeen.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** مسارات شاشات المهام (المسار الابتدائي Routes.TASKS_HOME = اليوم الحالي). القيم البديلة غير فارغة دائمًا لسلامة الملاحة. */
object TasksRoutes {
    const val NEW = "new"
    const val NONE = "none"

    const val DAY_PATTERN = "tasks/day?date={date}"
    fun day(date: LocalDate) = "tasks/day?date=$date"

    const val EDITOR_PATTERN = "tasks/edit?id={id}&preset={preset}"
    fun editor(id: String = NEW, preset: String = NONE) = "tasks/edit?id=$id&preset=$preset"

    const val PRESETS = "tasks/presets"

    /** ملخص اليوم الحي (يُفتح من إشعار نهاية اليوم). */
    const val DAY_SUMMARY_BASE = "tasks/day_summary"
    const val DAY_SUMMARY_PATTERN = "tasks/day_summary?date={date}"
    fun daySummary(date: LocalDate) = "tasks/day_summary?date=$date"
}

/** الكتابة والقراءة لبيانات المهام. كل الكتابة عبر writer (مزامنة)، وكل تغيير مؤثر على التذكيرات يطلب إعادة الجدولة. */
class TasksRepository(private val c: AppContainer) {
    private val taskDao get() = c.db.tasks()

    fun observeTasks(): Flow<List<TaskEntity>> = taskDao.observeActive()

    fun observePresetKeys(): Flow<Set<String>> = taskDao.observeActive().map { list -> list.map { it.preset }.filter { it.isNotBlank() }.toSet() }

    fun observePlans(): Flow<List<QuranPlanEntity>> = c.db.quranPlans().observeActive()

    suspend fun load(id: String): TaskEntity? = taskDao.byId(id)?.takeIf { !it.deleted }

    suspend fun save(draft: TaskDraft): TaskEntity {
        val now = c.clock.nowMillis()
        var entity = draft.toEntity(newId(), now)
        if (draft.id == null) {
            val max = taskDao.active().maxOfOrNull { it.sortOrder } ?: -1
            entity = entity.copy(sortOrder = max + 1)
        }
        val saved = c.writer.save(taskDao, entity.copy(deleted = false))
        c.requestReschedule()
        return saved
    }

    suspend fun delete(task: TaskEntity) {
        c.writer.delete(taskDao, task)
        c.requestReschedule()
    }

    /** يضيف مهمة جاهزة بنقرة واحدة. إن كانت مضافة فعلًا لا تتكرر. */
    suspend fun addPreset(p: TaskPreset): TaskEntity {
        val existing = taskDao.byId(p.taskId)
        if (existing != null && !existing.deleted) return existing
        val max = taskDao.active().maxOfOrNull { it.sortOrder } ?: -1
        val saved = c.writer.save(taskDao, p.toEntity(c.clock.today(), max + 1, c.clock.nowMillis()))
        c.requestReschedule()
        return saved
    }

    /**
     * علامة إنجاز يدوية (للمهام غير المرتبطة فقط). المرتبطة تُشتق من سجل العبادة ولا تُعدَّل هنا.
     * id حتمي "{taskId}_{date}" فلا تتكرر الإنجازات عند المزامنة.
     */
    suspend fun setManualDone(task: TaskEntity, date: LocalDate, done: Boolean) {
        if (task.linkEnum() != app.yaqeen.domain.model.TaskLink.NONE) return
        val dao = c.db.taskCompletions()
        val id = "${task.id}_${date.dayKey()}"
        val existing = dao.byId(id)
        if (done) {
            if (existing != null && !existing.deleted) return
            c.writer.save(dao, TaskCompletionEntity(id = id, taskId = task.id, date = date.dayKey(), completedAt = c.clock.nowMillis()))
        } else if (existing != null && !existing.deleted) {
            c.writer.delete(dao, existing)
        }
        if (task.reminderEnabled) c.requestReschedule()
    }
}
