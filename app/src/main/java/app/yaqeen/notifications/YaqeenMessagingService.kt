package app.yaqeen.notifications

import app.yaqeen.appContainer
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.settings.updates.UpdateMessage
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking

/**
 * استقبال إشعارات FCM. الرسائل المدعومة (data فقط):
 *   {type:"update", version:"1.2.3", url?:"https://…"} → إشعار "تحديث متاح" يفتح شاشة التحديثات.
 * تُحترم الإعدادات (NOTIF_UPDATES)، ولا يُعرض الإشعار إن كان الإصدار المُعلَن غير أحدث من المثبّت.
 */
class YaqeenMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // لا نخزّن التوكن في أي مكان: نشترك في موضوع عام فقط، والتوكن يديره FCM نفسه.
        if (!FcmTopics.isFirebaseReady(this)) return
        val enabled = runCatching { runBlocking { appContainer.settings.get(Settings.NOTIF_UPDATES) } }.getOrDefault(true)
        FcmTopics.applyUpdates(this, enabled)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val update = UpdateMessage.parse(message.data) ?: return
        // onMessageReceived يعمل على خيط خلفي، فالقراءة المتزامنة من Room/DataStore آمنة هنا.
        runCatching { runBlocking { UpdateNotifications.notifyIfNew(appContainer, update.version) } }
    }
}
