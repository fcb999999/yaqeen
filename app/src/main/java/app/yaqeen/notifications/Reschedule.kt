package app.yaqeen.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.yaqeen.AppContainer
import app.yaqeen.appContainer
import app.yaqeen.feature.adhkar.reminders.AdhkarReminders
import app.yaqeen.feature.calendar.reminders.EventReminders
import app.yaqeen.feature.prayer.reminders.PrayerReminders
import app.yaqeen.feature.quran.reminders.WirdReminders
import app.yaqeen.feature.tasks.reminders.DayEndReminders
import app.yaqeen.feature.tasks.reminders.TaskReminders

/**
 * ينسّق إعادة جدولة كل أنواع التذكيرات. لكل ميزة كائن مسؤول عن تذكيراتها باسم ثابت
 * (يملكه صاحب الميزة) وتُستدعى دالته rescheduleAll عند: الإقلاع، تغيّر الإعدادات، تغيّر البيانات، والعامل الدوري.
 * قاعدة: rescheduleAll يلغي تذكيرات ميزته القديمة (cancelByPrefix) ثم يجدول القادم بمفاتيح ثابتة، وتحترم إعداد التفعيل لكل نوع.
 */
object RescheduleCoordinator {
    suspend fun run(c: AppContainer) {
        val steps: List<Pair<String, suspend (AppContainer) -> Unit>> = listOf(
            "prayer" to { x -> PrayerReminders.rescheduleAll(x) },
            "wird" to { x -> WirdReminders.rescheduleAll(x) },
            "adhkar" to { x -> AdhkarReminders.rescheduleAll(x) },
            "tasks" to { x -> TaskReminders.rescheduleAll(x) },
            "day_end" to { x -> DayEndReminders.rescheduleAll(x) },
            "events" to { x -> EventReminders.rescheduleAll(x) },
        )
        // فشل نوع واحد لا يمنع بقية الأنواع.
        for ((_, step) in steps) runCatching { step(c) }
    }
}

/** يعيد الجدولة بعد إعادة تشغيل الجهاز أو تحديث التطبيق أو تغيّر الوقت/المنطقة الزمنية. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        app.yaqeen.work.Workers.rescheduleNow(context)
    }
}

class RescheduleWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        RescheduleCoordinator.run(applicationContext.appContainer)
        return Result.success()
    }
}
