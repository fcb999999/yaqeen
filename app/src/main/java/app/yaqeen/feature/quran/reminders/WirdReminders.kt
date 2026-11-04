package app.yaqeen.feature.quran.reminders

import app.yaqeen.AppContainer
import app.yaqeen.core.dayKey
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.quran.plans.PlanMath
import app.yaqeen.feature.quran.plans.PlanRepository
import app.yaqeen.notifications.Channels
import app.yaqeen.notifications.Reminder
import app.yaqeen.ui.navigation.Routes
import java.time.LocalDate

/**
 * تذكير الورد القرآني (مفاتيح تبدأ بـ "wird:").
 * لكل ورد نشط فُعّل تذكيره وله وقت: يُجدول تذكير الأيام الثلاثة القادمة (اليوم وغدًا وبعد غد) بالمفتاح
 * "wird:<planId>:<date>". يُتخطّى يوم تحقق مقداره، وأيام ما قبل البداية وما بعد تاريخ النهاية.
 */
object WirdReminders {
    const val PREFIX = "wird:"
    private const val DAYS_AHEAD = 3

    suspend fun rescheduleAll(c: AppContainer) {
        c.reminders.cancelByPrefix(PREFIX)
        if (!c.settings.get(Settings.NOTIF_WIRD)) return
        val plans = c.db.quranPlans().active().filter { it.reminderEnabled && PlanMath.parseTime(it.reminderTime) != null }
        if (plans.isEmpty()) return

        val math = PlanRepository.of(c).math()
        val zone = c.clock.zone()
        val now = c.clock.nowMillis()
        val today = c.clock.today()
        val todayKey = today.dayKey()
        val todayLogs: Map<String, QuranPlanLogEntity> =
            c.db.quranPlanLogs().range(todayKey, todayKey).associateBy { it.planId }

        for (plan in plans) {
            val (h, m) = PlanMath.parseTime(plan.reminderTime) ?: continue
            for (r in buildReminders(math, plan, todayLogs[plan.id], today)) {
                val trigger = r.date.atTime(h, m).atZone(zone).toInstant().toEpochMilli()
                if (trigger <= now) continue
                c.reminders.schedule(
                    Reminder(
                        key = "$PREFIX${plan.id}:${r.date}",
                        triggerAtMillis = trigger,
                        channelId = Channels.WIRD,
                        title = "ورد ${plan.name}",
                        text = "حان وقت وردك اليوم: ${r.portionLabel}",
                        route = Routes.quranPlans(plan.id),
                    ),
                )
            }
        }
    }

    data class Planned(val date: LocalDate, val portionLabel: String)

    /** المقاطع التي تستحق تذكيرًا (دالة خالصة قابلة للاختبار). */
    fun buildReminders(math: PlanMath, plan: QuranPlanEntity, todayLog: QuranPlanLogEntity?, today: LocalDate): List<Planned> {
        val start = LocalDate.parse(plan.startDate)
        val end = plan.endDate?.let(LocalDate::parse)
        // إن كان البدء في المستقبل فأول مقطع يخص يوم البداية لا اليوم.
        val skip = if (start.isAfter(today)) java.time.temporal.ChronoUnit.DAYS.between(today, start).toInt() else 0
        val portions = math.upcomingPortions(plan, if (skip > 0) null else todayLog, DAYS_AHEAD)
        val out = ArrayList<Planned>()
        for (i in skip until DAYS_AHEAD) {
            val date = today.plusDays(i.toLong())
            val portion = portions.getOrNull(i - skip) ?: continue
            if (end != null && date.isAfter(end)) continue
            if (i == 0 && todayLog?.targetMet == true) continue
            out += Planned(date, math.rangeLabel(portion))
        }
        return out
    }
}
