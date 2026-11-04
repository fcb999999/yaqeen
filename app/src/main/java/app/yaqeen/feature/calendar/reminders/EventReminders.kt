package app.yaqeen.feature.calendar.reminders

import app.yaqeen.AppContainer
import app.yaqeen.core.dayKey
import app.yaqeen.data.local.CalendarEventEntity
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.calendar.IslamicEvent
import app.yaqeen.feature.calendar.IslamicEvents
import app.yaqeen.feature.tasks.ArabicFormat
import app.yaqeen.notifications.Channels
import app.yaqeen.notifications.Reminder
import app.yaqeen.ui.navigation.Routes
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** تخطيط خالص لتذكيرات المناسبات والأحداث الشخصية (قابل للاختبار). */
object EventPlanner {
    /** نافذة الجدولة بالأيام (يُجدَّد كل 12 ساعة بواسطة العامل الدوري). */
    const val WINDOW_DAYS = 8
    val ISLAMIC_TIME: LocalTime = LocalTime.of(9, 0)

    /** تنبيهان لكل مناسبة: قبلها بيوم وفي يومها، الساعة 09:00. المفاتيح "event:islamic:<KEY>:<date>:pre|day". */
    fun planIslamic(events: List<IslamicEvent>, nowMillis: Long, zone: ZoneId): List<Reminder> {
        val out = mutableListOf<Reminder>()
        for (e in events) {
            if (!e.notifiable) continue
            val hijriText = e.hijri.format()
            val pre = e.date.minusDays(1).atTime(ISLAMIC_TIME).atZone(zone).toInstant().toEpochMilli()
            if (pre > nowMillis) {
                out += Reminder(
                    key = "event:islamic:${e.key}:${e.date}:pre",
                    triggerAtMillis = pre,
                    channelId = Channels.EVENTS,
                    title = "غدًا: ${e.nameAr}",
                    text = "يوافق غدًا $hijriText بحسب تقويم أم القرى، وقد يختلف بيوم تبعًا لرؤية الهلال.",
                    route = Routes.CALENDAR,
                )
            }
            val day = e.date.atTime(ISLAMIC_TIME).atZone(zone).toInstant().toEpochMilli()
            if (day > nowMillis) {
                out += Reminder(
                    key = "event:islamic:${e.key}:${e.date}:day",
                    triggerAtMillis = day,
                    channelId = Channels.EVENTS,
                    title = "اليوم: ${e.nameAr}",
                    text = "يوافق اليوم $hijriText بحسب تقويم أم القرى.",
                    route = Routes.CALENDAR,
                )
            }
        }
        return out
    }

    /** تذكير لكل حدث شخصي مفعّل: في وقته إن حُدد وإلا 09:00. المفتاح "event:personal:<id>". */
    fun planPersonal(events: List<CalendarEventEntity>, nowMillis: Long, zone: ZoneId): List<Reminder> {
        val out = mutableListOf<Reminder>()
        for (e in events) {
            if (e.deleted || !e.reminderEnabled) continue
            val date = ArabicFormat.parseDate(e.date) ?: continue
            val time = ArabicFormat.parseTime(e.timeOfDay)
            val at = date.atTime(time ?: ISLAMIC_TIME).atZone(zone).toInstant().toEpochMilli()
            if (at <= nowMillis) continue
            out += Reminder(
                key = "event:personal:${e.id}",
                triggerAtMillis = at,
                channelId = Channels.EVENTS,
                title = e.title,
                text = e.notes.ifBlank { if (time != null) "حان موعد حدثك في التقويم" else "لديك حدث اليوم في التقويم" },
                route = Routes.CALENDAR,
            )
        }
        return out
    }
}

/**
 * تنبيه اقتراب المناسبات الإسلامية وأحداث التقويم الشخصية (مفاتيح تبدأ بـ "event:").
 * المناسبات الإسلامية تتبع Settings.NOTIF_EVENTS؛ الأحداث الشخصية تتبع علامة التذكير في كل حدث.
 */
object EventReminders {
    suspend fun rescheduleAll(c: AppContainer) {
        c.reminders.cancelByPrefix("event:")
        val today: LocalDate = c.clock.today()
        val now = c.clock.nowMillis()
        val zone = c.clock.zone()
        val horizon = today.plusDays(EventPlanner.WINDOW_DAYS.toLong())

        if (c.settings.get(Settings.NOTIF_EVENTS)) {
            val adjust = c.settings.get(Settings.HIJRI_ADJUST_DAYS).coerceIn(-2, 2)
            val events = IslamicEvents.startingBetween(today, horizon, adjust)
            EventPlanner.planIslamic(events, now, zone).forEach { c.reminders.schedule(it) }
        }

        val personal = c.db.calendarEvents().upcoming(today.dayKey())
            .filter { e -> ArabicFormat.parseDate(e.date)?.let { !it.isAfter(horizon) } ?: false }
        EventPlanner.planPersonal(personal, now, zone).forEach { c.reminders.schedule(it) }
    }
}
