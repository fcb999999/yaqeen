package app.yaqeen.feature.adhkar.reminders

import app.yaqeen.AppContainer
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.AdhkarCategoryId
import app.yaqeen.feature.adhkar.AdhkarStats
import app.yaqeen.notifications.Channels
import app.yaqeen.notifications.Reminder
import app.yaqeen.ui.navigation.Routes

/**
 * تذكير أذكار الصباح والمساء والنوم (مفاتيح تبدأ بـ "adhkar:"):
 *  - كل تذكير يُفعَّل بمفتاحه ويُضبط وقته من الإعدادات (NOTIF_MORNING/EVENING/SLEEP + أوقاتها).
 *  - يُجدوَل اليوم والأيام التالية (٣ أيام) بمفاتيح ثابتة "adhkar:<category>:<date>" فلا تكرار.
 *  - لا يُجدوَل تذكير لقسم أُنجز في ذلك اليوم.
 */
object AdhkarReminders {
    suspend fun rescheduleAll(c: AppContainer) {
        c.reminders.cancelByPrefix(AdhkarReminderPlanner.KEY_PREFIX)

        val slots = listOf(
            AdhkarReminderPlanner.Slot(
                AdhkarCategoryId.MORNING, c.settings.get(Settings.NOTIF_MORNING),
                c.settings.get(Settings.NOTIF_MORNING_TIME), Settings.NOTIF_MORNING_TIME.default,
            ),
            AdhkarReminderPlanner.Slot(
                AdhkarCategoryId.EVENING, c.settings.get(Settings.NOTIF_EVENING),
                c.settings.get(Settings.NOTIF_EVENING_TIME), Settings.NOTIF_EVENING_TIME.default,
            ),
            AdhkarReminderPlanner.Slot(
                AdhkarCategoryId.SLEEP, c.settings.get(Settings.NOTIF_SLEEP),
                c.settings.get(Settings.NOTIF_SLEEP_TIME), Settings.NOTIF_SLEEP_TIME.default,
            ),
        )
        if (slots.none { it.enabled }) return

        val today = c.clock.today()
        val last = today.plusDays((AdhkarReminderPlanner.DAYS_AHEAD - 1).toLong())
        val categories = c.adhkar().categories
        val rows = c.db.adhkarProgress().range(today.toString(), last.toString())
        val completedByDate = rows.groupBy { it.date }.mapValues { (_, dayRows) -> AdhkarStats.completedCategoryIds(categories, dayRows) }

        val planned = AdhkarReminderPlanner.plan(c.clock.nowMillis(), c.clock.zone(), today, slots, completedByDate)
        for (p in planned) {
            c.reminders.schedule(
                Reminder(
                    key = p.key,
                    triggerAtMillis = p.triggerAtMillis,
                    channelId = Channels.ADHKAR,
                    title = AdhkarReminderPlanner.title(p.categoryId),
                    text = AdhkarReminderPlanner.body(p.categoryId),
                    route = Routes.adhkarCategory(p.categoryId),
                ),
            )
        }
    }
}
