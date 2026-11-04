package app.yaqeen.feature.prayer.adhan

import android.content.Context
import app.yaqeen.notifications.ReminderPayload

/**
 * يعالج تذكير قناة الأذان (تشغيل الصوت المختار وعرض الإشعار). يعيد true إن عالجه بالكامل،
 * وfalse ليعرض النظام الإشعار الافتراضي. (يستبدلها منفذ ميزة الصلاة)
 */
object AdhanPlayer {
    fun handle(context: Context, payload: ReminderPayload): Boolean = false
}
