package app.yaqeen.feature.tasks.reminders

import app.yaqeen.AppContainer

/** تذكير المهام (مفاتيح تبدأ بـ "task:"). (يستبدلها منفذ ميزة المهام) */
object TaskReminders {
    suspend fun rescheduleAll(c: AppContainer) {}
}

/** تنبيه نهاية اليوم بالملخص (مفاتيح تبدأ بـ "dayend:"). الافتراضي 23:00. */
object DayEndReminders {
    suspend fun rescheduleAll(c: AppContainer) {}
}
