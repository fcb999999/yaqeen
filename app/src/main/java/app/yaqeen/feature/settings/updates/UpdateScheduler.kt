package app.yaqeen.feature.settings.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.yaqeen.BuildConfig
import app.yaqeen.appContainer
import app.yaqeen.data.prefs.LocalPrefs
import app.yaqeen.data.settings.Settings
import app.yaqeen.notifications.FcmTopics
import app.yaqeen.notifications.UpdateNotifications
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * فحص خلفي خفيف كل 24 ساعة (يتطلب اتصالًا بالإنترنت) ينشر إشعار "تحديث متاح" عند وجود إصدار أحدث.
 * يعمل فقط إذا: مستودع GitHub مضبوط، إشعارات التحديثات مفعّلة، والتطبيق لم يُثبَّت من Google Play.
 */
object UpdateScheduler {
    private const val WORK_NAME = "yaqeen-update-check"

    /**
     * يقرأ إعداد NOTIF_UPDATES ويضبط العامل الدوري واشتراك FCM وفقه. استدعِه من YaqeenApp.onCreate
     * (وتستدعيه شاشة الإعدادات تلقائيًا عند تغيير المفتاح).
     */
    fun schedule(context: Context) {
        val c = context.applicationContext.appContainer
        c.appScope.launch {
            val enabled = runCatching { c.settings.get(Settings.NOTIF_UPDATES) }.getOrDefault(true)
            apply(context.applicationContext, enabled)
        }
    }

    /** يطبّق حالة الإشعارات مباشرة (يُستخدم عند تبديل المفتاح دون إعادة قراءة الإعداد). */
    fun apply(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        val possible = enabled &&
            UpdateChecker.isValidRepo(BuildConfig.GITHUB_REPO) &&
            !UpdateInstaller.isInstalledFromPlayStore(context)
        if (possible) {
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<UpdateWorker>(24, TimeUnit.HOURS)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .setInitialDelay(1, TimeUnit.HOURS)
                    .build(),
            )
        } else {
            wm.cancelUniqueWork(WORK_NAME)
        }
        FcmTopics.applyUpdates(context, enabled)
    }
}

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        if (!c.settings.get(Settings.NOTIF_UPDATES)) return Result.success()
        val repo = BuildConfig.GITHUB_REPO
        if (!UpdateChecker.isValidRepo(repo) || UpdateInstaller.isInstalledFromPlayStore(applicationContext)) return Result.success()

        val result = withContext(Dispatchers.IO) { UpdateChecker(UrlHttp(), repo, BuildConfig.VERSION_NAME).check() }
        c.localPrefs.put(LocalPrefs.Keys.LAST_UPDATE_CHECK, System.currentTimeMillis().toString())
        return when (result) {
            is UpdateCheckResult.Available -> {
                UpdateNotifications.notifyIfNew(c, SemVer.parse(result.release.tag)?.toString() ?: result.release.tag)
                Result.success()
            }
            is UpdateCheckResult.Failed -> if (runAttemptCount < 3) Result.retry() else Result.success()
            else -> Result.success() // لا جديد / دون اتصال / حد الطلبات: ننتظر الدورة التالية
        }
    }
}
