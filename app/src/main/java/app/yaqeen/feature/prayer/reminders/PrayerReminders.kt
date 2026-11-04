package app.yaqeen.feature.prayer.reminders

import app.yaqeen.AppContainer

/** جدولة الأذان والتنبيه قبل الصلاة والإقامة (مفاتيح تبدأ بـ "prayer:"). (يستبدلها منفذ ميزة الصلاة) */
object PrayerReminders {
    suspend fun rescheduleAll(c: AppContainer) {}
}
