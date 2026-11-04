package app.yaqeen.notifications

import android.content.Context
import app.yaqeen.AppContainer
import app.yaqeen.BuildConfig
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.settings.updates.SemVer
import app.yaqeen.ui.navigation.Routes

/** إشعار "تحديث متاح" (القناة UPDATES). يُفتح بالنقر على شاشة التحديثات. */
object UpdateNotifications {
    /** آخر إصدار أُشعر المستخدم به (حتى لا يتكرر الإشعار نفسه من العامل الدوري ومن FCM). */
    const val PREF_LAST_NOTIFIED = "update.last_notified"

    fun post(context: Context, version: String) {
        NotificationPoster.post(
            context,
            ReminderPayload(
                key = "update:$version",
                channelId = Channels.UPDATES,
                title = "تحديث جديد ليقين",
                text = "الإصدار ${app.yaqeen.core.ArabicText.indic(version)} متاح الآن. اضغط لعرض الجديد وتحديث التطبيق.",
                route = Routes.SETTINGS_UPDATES,
                extras = emptyMap(),
            ),
        )
    }

    /**
     * يعرض الإشعار إن كان [version] أحدث من المثبّت، والمستخدم مفعّل إشعارات التحديثات، ولم يُشعَر بهذا الإصدار من قبل.
     * يعيد true إذا عُرض الإشعار.
     */
    suspend fun notifyIfNew(c: AppContainer, version: String): Boolean {
        if (!SemVer.isNewer(version, BuildConfig.VERSION_NAME)) return false
        if (!c.settings.get(Settings.NOTIF_UPDATES)) return false
        if (c.localPrefs.get(PREF_LAST_NOTIFIED) == version) return false
        post(c.context, version)
        c.localPrefs.put(PREF_LAST_NOTIFIED, version)
        return true
    }
}
