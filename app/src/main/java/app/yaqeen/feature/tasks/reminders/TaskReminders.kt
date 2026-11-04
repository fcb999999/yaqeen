package app.yaqeen.feature.tasks.reminders

import app.yaqeen.AppContainer
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.TaskLink
import app.yaqeen.feature.tasks.ArabicFormat
import app.yaqeen.feature.tasks.DayEndSummary
import app.yaqeen.feature.tasks.TasksRoutes
import app.yaqeen.feature.tasks.TodayTask
import app.yaqeen.feature.tasks.linkEnum
import app.yaqeen.notifications.Channels
import app.yaqeen.notifications.Reminder
import app.yaqeen.ui.navigation.Routes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** تخطيط خالص (قابل للاختبار) لتذكيرات المهام ولتنبيه نهاية اليوم. */
object ReminderPlanner {
    /** عدد الأيام القادمة التي تُجدول (اليوم + التالي). */
    const val TASK_DAYS = 3
    const val DAY_END_DAYS = 2
    const val DEFAULT_DAY_END_TIME = "23:00"

    /** تذكير لكل مهمة مفعّل تذكيرها ولها وقت ولم تُنجز في ذلك اليوم؛ المفتاح "task:<taskId>:<date>". */
    fun planTasks(days: List<Pair<LocalDate, List<TodayTask>>>, nowMillis: Long, zone: ZoneId): List<Reminder> {
        val out = mutableListOf<Reminder>()
        for ((date, tasks) in days) {
            for (t in tasks) {
                val task = t.task
                if (!task.reminderEnabled || t.done) continue
                val time = ArabicFormat.parseTime(task.timeOfDay) ?: continue
                val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
                if (at <= nowMillis) continue
                val text = when {
                    task.notes.isNotBlank() -> task.notes
                    task.linkEnum() != TaskLink.NONE -> "حان وقت: ${task.linkEnum().ar}"
                    else -> "حان موعد مهمتك اليومية"
                }
                out += Reminder(
                    key = "task:${task.id}:$date",
                    triggerAtMillis = at,
                    channelId = Channels.TASKS,
                    title = task.title,
                    text = text,
                    route = Routes.TASKS,
                )
            }
        }
        return out
    }

    /**
     * تنبيه نهاية اليوم: "dayend:<date>" لكل يوم. نصه عام ثابت (الملخص الحي يُحسب عند فتح الشاشة).
     * إن كان الوقت بعد منتصف الليل (قبل 03:00) يُطلق في اليوم التالي لكنه يخص اليوم المنتهي.
     */
    fun planDayEnd(firstDate: LocalDate, timeHHmm: String, nowMillis: Long, zone: ZoneId): List<Reminder> {
        val time: LocalTime = ArabicFormat.parseTime(timeHHmm) ?: ArabicFormat.parseTime(DEFAULT_DAY_END_TIME)!!
        val out = mutableListOf<Reminder>()
        // بعد منتصف الليل: ملخص أمس قد يحين وقته اليوم، فنبدأ من أمس.
        val start = if (time.hour < 3) firstDate.minusDays(1) else firstDate
        for (i in 0 until DAY_END_DAYS) {
            val date = start.plusDays(i.toLong())
            val fireDate = if (time.hour < 3) date.plusDays(1) else date
            val at = fireDate.atTime(time).atZone(zone).toInstant().toEpochMilli()
            if (at <= nowMillis) continue
            out += Reminder(
                key = "dayend:$date",
                triggerAtMillis = at,
                channelId = Channels.DAY_END,
                title = DayEndSummary.GENERIC_NOTIFICATION_TITLE,
                text = DayEndSummary.GENERIC_NOTIFICATION_TEXT,
                route = TasksRoutes.daySummary(date),
            )
        }
        return out
    }
}

/** تذكير المهام (مفاتيح تبدأ بـ "task:"): للأيام الثلاثة القادمة، للمهام المفعّل تذكيرها ولها وقت. */
object TaskReminders {
    suspend fun rescheduleAll(c: AppContainer) {
        c.reminders.cancelByPrefix("task:")
        if (!c.settings.get(Settings.NOTIF_TASKS)) return
        val today = c.clock.today()
        val now = c.clock.nowMillis()
        val days = (0 until ReminderPlanner.TASK_DAYS).map { i ->
            val d = today.plusDays(i.toLong())
            d to c.taskEngine.dayOnce(d)
        }
        ReminderPlanner.planTasks(days, now, c.clock.zone()).forEach { c.reminders.schedule(it) }
    }
}

/** تنبيه نهاية اليوم بالملخص (مفاتيح تبدأ بـ "dayend:"). الافتراضي 23:00. */
object DayEndReminders {
    suspend fun rescheduleAll(c: AppContainer) {
        c.reminders.cancelByPrefix("dayend:")
        if (!c.settings.get(Settings.NOTIF_DAY_END)) return
        val time = c.settings.get(Settings.NOTIF_DAY_END_TIME)
        ReminderPlanner.planDayEnd(c.clock.today(), time, c.clock.nowMillis(), c.clock.zone()).forEach { c.reminders.schedule(it) }
    }
}
