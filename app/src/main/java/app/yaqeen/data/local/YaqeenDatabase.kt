package app.yaqeen.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ReadingProgressEntity::class,
        BookmarkCategoryEntity::class,
        BookmarkEntity::class,
        QuranPlanEntity::class,
        QuranPlanLogEntity::class,
        QuranDayLogEntity::class,
        AdhkarProgressEntity::class,
        AdhkarMarkEntity::class,
        PrayerLogEntity::class,
        RawatibLogEntity::class,
        CustomSunnahEntity::class,
        TaskEntity::class,
        TaskCompletionEntity::class,
        CalendarEventEntity::class,
        SettingEntity::class,
        DailySummaryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class YaqeenDatabase : RoomDatabase() {
    abstract fun readingProgress(): ReadingProgressDao
    abstract fun bookmarkCategories(): BookmarkCategoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun quranPlans(): QuranPlanDao
    abstract fun quranPlanLogs(): QuranPlanLogDao
    abstract fun quranDayLogs(): QuranDayLogDao
    abstract fun adhkarProgress(): AdhkarProgressDao
    abstract fun adhkarMarks(): AdhkarMarkDao
    abstract fun prayerLogs(): PrayerLogDao
    abstract fun rawatibLogs(): RawatibLogDao
    abstract fun customSunnah(): CustomSunnahDao
    abstract fun tasks(): TaskDao
    abstract fun taskCompletions(): TaskCompletionDao
    abstract fun calendarEvents(): CalendarEventDao
    abstract fun settings(): SettingDao
    abstract fun dailySummaries(): DailySummaryDao

    companion object {
        const val NAME = "yaqeen.db"

        fun create(context: Context): YaqeenDatabase =
            Room.databaseBuilder(context.applicationContext, YaqeenDatabase::class.java, NAME)
                // لا نستخدم fallbackToDestructiveMigration أبدًا: بيانات المستخدم يجب أن تُحفظ عند التحديث.
                .build()
    }
}
