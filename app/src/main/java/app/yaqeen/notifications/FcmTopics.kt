package app.yaqeen.notifications

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging

/**
 * اشتراكات مواضيع FCM. كل الاستدعاءات محروسة: بلا Firebase مُعدّ (لا google-services.json) لا يحدث شيء ولا يتعطل التطبيق.
 */
object FcmTopics {
    /** موضوع إشعارات الإصدارات الجديدة (يرسل إليه tools/notify-release.mjs). */
    const val UPDATES = "app_updates"

    fun isFirebaseReady(context: Context): Boolean =
        runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    /** يشترك في موضوع التحديثات أو يلغي الاشتراك حسب [enabled] (يتبع إعداد NOTIF_UPDATES). */
    fun applyUpdates(context: Context, enabled: Boolean) = apply(context, UPDATES, enabled)

    fun apply(context: Context, topic: String, enabled: Boolean) {
        if (!isFirebaseReady(context)) return
        runCatching {
            val fm = FirebaseMessaging.getInstance()
            if (enabled) fm.subscribeToTopic(topic) else fm.unsubscribeFromTopic(topic)
        }
    }
}
