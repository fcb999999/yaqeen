package app.yaqeen.feature.calendar.reminders

import app.yaqeen.AppContainer

/** تنبيه اقتراب المناسبات الإسلامية وأحداث التقويم الشخصية (مفاتيح تبدأ بـ "event:"). (يستبدلها منفذ ميزة التقويم) */
object EventReminders {
    suspend fun rescheduleAll(c: AppContainer) {}
}
