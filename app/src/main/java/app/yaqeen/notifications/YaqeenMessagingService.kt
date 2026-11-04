package app.yaqeen.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/** استقبال إشعارات FCM (تحديثات التطبيق). (يستبدلها منفذ ميزة الإعدادات/التحديثات) */
class YaqeenMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {}
}
