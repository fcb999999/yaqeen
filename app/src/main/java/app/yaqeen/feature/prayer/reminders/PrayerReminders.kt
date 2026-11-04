package app.yaqeen.feature.prayer.reminders

import app.yaqeen.AppContainer
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.Prayer
import app.yaqeen.feature.prayer.times.PrayerFormat
import app.yaqeen.feature.prayer.times.PrayerTimesDay
import app.yaqeen.notifications.Channels
import app.yaqeen.notifications.Reminder
import app.yaqeen.ui.navigation.Routes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** إعدادات تنبيهات صلاة واحدة (من Settings.notif*). */
data class PrayerNotifPrefs(
    /** الأذان عند دخول الوقت. */
    val adhan: Boolean = true,
    /** دقائق التنبيه قبل الصلاة (0 = معطّل). */
    val preMinutes: Int = 10,
    /** دقائق تنبيه الإقامة بعد الأذان (0 = معطّل). */
    val iqamaMinutes: Int = 0,
    /** "default" | "file:<اسم>" | "raw:<اسم>". */
    val voice: String = "default",
)

enum class PrayerReminderKind(val id: String) { ADHAN("adhan"), PRE("pre"), IQAMA("iqama") }

data class PlannedReminder(
    val key: String,
    val at: ZonedDateTime,
    val prayer: Prayer,
    val kind: PrayerReminderKind,
    val minutes: Int,
    /** وقت أذان هذه الصلاة (للعرض في نص التنبيه). */
    val adhanAt: ZonedDateTime,
)

/** التخطيط الخالص (بلا Android) لتذكيرات الصلاة: يُختبر على JVM. */
object PrayerReminderPlanner {
    const val PREFIX = "prayer:"
    const val DAYS_AHEAD = 3

    fun key(date: LocalDate, prayer: Prayer, kind: PrayerReminderKind) = "$PREFIX$date:${prayer.name}:${kind.id}"

    /** يخطط تذكيرات الأيام المعطاة، مستبعدًا ما فات وقته أو ما كان معطّلًا. مرتبة زمنيًا. */
    fun plan(days: List<PrayerTimesDay>, prefs: Map<Prayer, PrayerNotifPrefs>, now: Instant): List<PlannedReminder> {
        val out = ArrayList<PlannedReminder>()
        for (day in days) for (p in Prayer.entries) {
            val pref = prefs[p] ?: continue
            val adhanTime = day.timeOf(p)
            if (pref.adhan) out += PlannedReminder(key(day.date, p, PrayerReminderKind.ADHAN), adhanTime, p, PrayerReminderKind.ADHAN, 0, adhanTime)
            if (pref.preMinutes > 0) {
                out += PlannedReminder(key(day.date, p, PrayerReminderKind.PRE), adhanTime.minusMinutes(pref.preMinutes.toLong()), p, PrayerReminderKind.PRE, pref.preMinutes, adhanTime)
            }
            if (pref.iqamaMinutes > 0) {
                out += PlannedReminder(key(day.date, p, PrayerReminderKind.IQAMA), adhanTime.plusMinutes(pref.iqamaMinutes.toLong()), p, PrayerReminderKind.IQAMA, pref.iqamaMinutes, adhanTime)
            }
        }
        return out.filter { it.at.toInstant().isAfter(now) }.sortedBy { it.at.toInstant() }
    }

    fun title(r: PlannedReminder): String = when (r.kind) {
        PrayerReminderKind.ADHAN -> "حان وقت صلاة ${r.prayer.ar}"
        PrayerReminderKind.PRE -> "صلاة ${r.prayer.ar} بعد ${PrayerFormat.minutes(r.minutes)}"
        PrayerReminderKind.IQAMA -> "موعد إقامة صلاة ${r.prayer.ar}"
    }

    /** [adhanClock] وقت الأذان منسّقًا، [city] اسم المدينة. */
    fun text(r: PlannedReminder, adhanClock: String, city: String): String = when (r.kind) {
        PrayerReminderKind.ADHAN -> "$city – $adhanClock"
        PrayerReminderKind.PRE -> "الأذان الساعة $adhanClock – $city"
        PrayerReminderKind.IQAMA -> "مضت ${PrayerFormat.minutes(r.minutes)} على أذان ${r.prayer.ar} – $city"
    }
}

/** جدولة الأذان والتنبيه قبل الصلاة والإقامة (مفاتيح تبدأ بـ "prayer:"). */
object PrayerReminders {
    suspend fun rescheduleAll(c: AppContainer) {
        // نلغي القديم أولًا (يشمل حالة حذف الموقع أو تعطيل كل التنبيهات).
        c.reminders.cancelByPrefix(PrayerReminderPlanner.PREFIX)
        val loc = c.prayerTimes.currentLocation() ?: return
        val calc = c.prayerTimes.calcSettings()
        val zone = ZoneId.of(loc.zoneId)
        val now = Instant.now()
        val today = now.atZone(zone).toLocalDate()

        val prefs = Prayer.entries.associateWith { p ->
            PrayerNotifPrefs(
                adhan = c.settings.get(Settings.notifAdhan(p.name)),
                preMinutes = c.settings.get(Settings.notifPreMinutes(p.name)),
                iqamaMinutes = c.settings.get(Settings.notifIqamaMinutes(p.name)),
                voice = c.settings.get(Settings.adhanVoice(p.name)),
            )
        }
        val days = (0 until PrayerReminderPlanner.DAYS_AHEAD).mapNotNull { c.prayerTimes.timesFor(today.plusDays(it.toLong()), loc, calc) }
        val is24h = android.text.format.DateFormat.is24HourFormat(c.context)

        for (r in PrayerReminderPlanner.plan(days, prefs, now)) {
            val adhanClock = PrayerFormat.clock(r.adhanAt, is24h)
            c.reminders.schedule(
                Reminder(
                    key = r.key,
                    triggerAtMillis = r.at.toInstant().toEpochMilli(),
                    channelId = if (r.kind == PrayerReminderKind.ADHAN) Channels.ADHAN else Channels.PRAYER_REMINDER,
                    title = PrayerReminderPlanner.title(r),
                    text = PrayerReminderPlanner.text(r, adhanClock, loc.name),
                    route = Routes.PRAYER_HOME,
                    extras = mapOf("prayer" to r.prayer.name, "voice" to (prefs[r.prayer]?.voice ?: "default")),
                ),
            )
        }
    }
}
