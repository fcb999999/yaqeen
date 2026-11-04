package app.yaqeen.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yaqeen.appContainer
import app.yaqeen.notifications.RescheduleWorker
import java.util.concurrent.TimeUnit

/** مزامنة دورية في الخلفية عند توفر الإنترنت (كل 15 دقيقة كحد أدنى لنظام Android). */
class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        c.sync.syncNow()
        // تنظيف شواهد الحذف الأقدم من 30 يومًا والتي وصلت للسحابة
        c.sync.purgeOldTombstones(30L * 24 * 3600 * 1000)
        return Result.success()
    }
}

object Workers {
    private const val SYNC = "yaqeen-sync"
    private const val RESCHEDULE_PERIODIC = "yaqeen-reschedule-periodic"
    private const val RESCHEDULE_NOW = "yaqeen-reschedule-now"

    /** تُستدعى عند إقلاع التطبيق. لا خدمات خلفية دائمة: عاملان دوريان فقط. */
    fun schedulePeriodic(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            SYNC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        // يتجدد كل 12 ساعة ليبقي نافذة التذكيرات القادمة ممتلئة (مواقيت الأيام التالية، مناسبات...).
        wm.enqueueUniquePeriodicWork(
            RESCHEDULE_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RescheduleWorker>(12, TimeUnit.HOURS).build(),
        )
    }

    fun rescheduleNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            RESCHEDULE_NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<RescheduleWorker>().build(),
        )
    }
}
