package app.yaqeen.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ملف مُولَّد جزئيًا: كل DAO يحتوي أساسيات المزامنة ثم استعلامات الميزة. يمكن إضافة استعلامات جديدة في نهاية أي DAO
// (دون تغيير الأعمدة أو الجداول، فتغيير المخطط يحتاج ترحيل Room ويقرره صاحب قاعدة البيانات).

/** عمليات المزامنة المشتركة لكل جدول. */
interface SyncDao<T> {
    suspend fun dirty(): List<@JvmSuppressWildcards T>
    suspend fun byId(id: String): T?
    suspend fun upsertAll(rows: List<@JvmSuppressWildcards T>)
    suspend fun markClean(id: String, opId: String)
    suspend fun purgeDeleted(before: Long)
}

@Dao
interface ReadingProgressDao : SyncDao<ReadingProgressEntity> {
    @Query("SELECT * FROM reading_progress WHERE dirty = 1")
    override suspend fun dirty(): List<ReadingProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE id = :id")
    override suspend fun byId(id: String): ReadingProgressEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<ReadingProgressEntity>)

    @Upsert
    suspend fun upsert(row: ReadingProgressEntity)

    @Query("UPDATE reading_progress SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM reading_progress WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM reading_progress WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM reading_progress WHERE deleted = 0 ORDER BY lastReadAt DESC")
    fun observeAll(): Flow<List<ReadingProgressEntity>>

    /** أحدث موضع قراءة بين كل الأجهزة. */
    @Query("SELECT * FROM reading_progress WHERE deleted = 0 ORDER BY lastReadAt DESC LIMIT 1")
    fun observeLatest(): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress WHERE deleted = 0 ORDER BY lastReadAt DESC LIMIT 1")
    suspend fun latest(): ReadingProgressEntity?
}

@Dao
interface BookmarkCategoryDao : SyncDao<BookmarkCategoryEntity> {
    @Query("SELECT * FROM quran_bookmark_categories WHERE dirty = 1")
    override suspend fun dirty(): List<BookmarkCategoryEntity>

    @Query("SELECT * FROM quran_bookmark_categories WHERE id = :id")
    override suspend fun byId(id: String): BookmarkCategoryEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<BookmarkCategoryEntity>)

    @Upsert
    suspend fun upsert(row: BookmarkCategoryEntity)

    @Query("UPDATE quran_bookmark_categories SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM quran_bookmark_categories WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM quran_bookmark_categories WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM quran_bookmark_categories WHERE deleted = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<BookmarkCategoryEntity>>
}

@Dao
interface BookmarkDao : SyncDao<BookmarkEntity> {
    @Query("SELECT * FROM quran_bookmarks WHERE dirty = 1")
    override suspend fun dirty(): List<BookmarkEntity>

    @Query("SELECT * FROM quran_bookmarks WHERE id = :id")
    override suspend fun byId(id: String): BookmarkEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<BookmarkEntity>)

    @Upsert
    suspend fun upsert(row: BookmarkEntity)

    @Query("UPDATE quran_bookmarks SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM quran_bookmarks WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM quran_bookmarks WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM quran_bookmarks WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM quran_bookmarks WHERE deleted = 0 AND categoryId = :categoryId ORDER BY createdAt DESC")
    fun observeByCategory(categoryId: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM quran_bookmarks WHERE deleted = 0 AND surah = :surah AND ayah = :ayah LIMIT 1")
    suspend fun findAt(surah: Int, ayah: Int): BookmarkEntity?

    /** كل علامات صفحة معيّنة (لإظهار المؤشر في المصحف). */
    @Query("SELECT * FROM quran_bookmarks WHERE deleted = 0 AND page = :page")
    fun observeForPage(page: Int): Flow<List<BookmarkEntity>>
}

@Dao
interface QuranPlanDao : SyncDao<QuranPlanEntity> {
    @Query("SELECT * FROM quran_plans WHERE dirty = 1")
    override suspend fun dirty(): List<QuranPlanEntity>

    @Query("SELECT * FROM quran_plans WHERE id = :id")
    override suspend fun byId(id: String): QuranPlanEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<QuranPlanEntity>)

    @Upsert
    suspend fun upsert(row: QuranPlanEntity)

    @Query("UPDATE quran_plans SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM quran_plans WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM quran_plans WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM quran_plans WHERE deleted = 0 ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<QuranPlanEntity>>

    @Query("SELECT * FROM quran_plans WHERE deleted = 0 AND status = 'ACTIVE' ORDER BY createdAt DESC")
    fun observeActive(): Flow<List<QuranPlanEntity>>

    @Query("SELECT * FROM quran_plans WHERE deleted = 0 AND status = 'ACTIVE'")
    suspend fun active(): List<QuranPlanEntity>

    @Query("SELECT * FROM quran_plans WHERE id = :id AND deleted = 0")
    fun observe(id: String): Flow<QuranPlanEntity?>
}

@Dao
interface QuranPlanLogDao : SyncDao<QuranPlanLogEntity> {
    @Query("SELECT * FROM quran_plan_logs WHERE dirty = 1")
    override suspend fun dirty(): List<QuranPlanLogEntity>

    @Query("SELECT * FROM quran_plan_logs WHERE id = :id")
    override suspend fun byId(id: String): QuranPlanLogEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<QuranPlanLogEntity>)

    @Upsert
    suspend fun upsert(row: QuranPlanLogEntity)

    @Query("UPDATE quran_plan_logs SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM quran_plan_logs WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM quran_plan_logs WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM quran_plan_logs WHERE deleted = 0 AND planId = :planId ORDER BY date DESC")
    fun observeForPlan(planId: String): Flow<List<QuranPlanLogEntity>>

    @Query("SELECT * FROM quran_plan_logs WHERE deleted = 0 AND date BETWEEN :from AND :to")
    fun observeRange(from: String, to: String): Flow<List<QuranPlanLogEntity>>

    @Query("SELECT * FROM quran_plan_logs WHERE deleted = 0 AND date BETWEEN :from AND :to")
    suspend fun range(from: String, to: String): List<QuranPlanLogEntity>
}

@Dao
interface QuranDayLogDao : SyncDao<QuranDayLogEntity> {
    @Query("SELECT * FROM quran_day_logs WHERE dirty = 1")
    override suspend fun dirty(): List<QuranDayLogEntity>

    @Query("SELECT * FROM quran_day_logs WHERE id = :id")
    override suspend fun byId(id: String): QuranDayLogEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<QuranDayLogEntity>)

    @Upsert
    suspend fun upsert(row: QuranDayLogEntity)

    @Query("UPDATE quran_day_logs SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM quran_day_logs WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM quran_day_logs WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM quran_day_logs WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<QuranDayLogEntity>>

    @Query("SELECT * FROM quran_day_logs WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date")
    suspend fun range(from: String, to: String): List<QuranDayLogEntity>

    @Query("SELECT * FROM quran_day_logs WHERE deleted = 0 ORDER BY date DESC")
    fun observeAll(): Flow<List<QuranDayLogEntity>>
}

@Dao
interface AdhkarProgressDao : SyncDao<AdhkarProgressEntity> {
    @Query("SELECT * FROM adhkar_progress WHERE dirty = 1")
    override suspend fun dirty(): List<AdhkarProgressEntity>

    @Query("SELECT * FROM adhkar_progress WHERE id = :id")
    override suspend fun byId(id: String): AdhkarProgressEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<AdhkarProgressEntity>)

    @Upsert
    suspend fun upsert(row: AdhkarProgressEntity)

    @Query("UPDATE adhkar_progress SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM adhkar_progress WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM adhkar_progress WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM adhkar_progress WHERE deleted = 0 AND date = :date")
    fun observeDay(date: String): Flow<List<AdhkarProgressEntity>>

    @Query("SELECT * FROM adhkar_progress WHERE deleted = 0 AND date = :date AND categoryId = :categoryId")
    fun observeDayCategory(date: String, categoryId: String): Flow<List<AdhkarProgressEntity>>

    @Query("SELECT * FROM adhkar_progress WHERE deleted = 0 AND date BETWEEN :from AND :to")
    fun observeRange(from: String, to: String): Flow<List<AdhkarProgressEntity>>

    @Query("SELECT * FROM adhkar_progress WHERE deleted = 0 AND date BETWEEN :from AND :to")
    suspend fun range(from: String, to: String): List<AdhkarProgressEntity>

    @Query("SELECT * FROM adhkar_progress WHERE deleted = 0 AND date = :date AND categoryId = :categoryId")
    suspend fun dayCategory(date: String, categoryId: String): List<AdhkarProgressEntity>
}

@Dao
interface AdhkarMarkDao : SyncDao<AdhkarMarkEntity> {
    @Query("SELECT * FROM adhkar_favorites WHERE dirty = 1")
    override suspend fun dirty(): List<AdhkarMarkEntity>

    @Query("SELECT * FROM adhkar_favorites WHERE id = :id")
    override suspend fun byId(id: String): AdhkarMarkEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<AdhkarMarkEntity>)

    @Upsert
    suspend fun upsert(row: AdhkarMarkEntity)

    @Query("UPDATE adhkar_favorites SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM adhkar_favorites WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM adhkar_favorites WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM adhkar_favorites WHERE deleted = 0 AND type = :type ORDER BY createdAt DESC")
    fun observeByType(type: String): Flow<List<AdhkarMarkEntity>>

    @Query("SELECT * FROM adhkar_favorites WHERE deleted = 0")
    fun observeAll(): Flow<List<AdhkarMarkEntity>>
}

@Dao
interface PrayerLogDao : SyncDao<PrayerLogEntity> {
    @Query("SELECT * FROM prayers_tracking WHERE dirty = 1")
    override suspend fun dirty(): List<PrayerLogEntity>

    @Query("SELECT * FROM prayers_tracking WHERE id = :id")
    override suspend fun byId(id: String): PrayerLogEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<PrayerLogEntity>)

    @Upsert
    suspend fun upsert(row: PrayerLogEntity)

    @Query("UPDATE prayers_tracking SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM prayers_tracking WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM prayers_tracking WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM prayers_tracking WHERE deleted = 0 AND date = :date")
    fun observeDay(date: String): Flow<List<PrayerLogEntity>>

    @Query("SELECT * FROM prayers_tracking WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<PrayerLogEntity>>

    @Query("SELECT * FROM prayers_tracking WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date")
    suspend fun range(from: String, to: String): List<PrayerLogEntity>
}

@Dao
interface RawatibLogDao : SyncDao<RawatibLogEntity> {
    @Query("SELECT * FROM rawatib_tracking WHERE dirty = 1")
    override suspend fun dirty(): List<RawatibLogEntity>

    @Query("SELECT * FROM rawatib_tracking WHERE id = :id")
    override suspend fun byId(id: String): RawatibLogEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<RawatibLogEntity>)

    @Upsert
    suspend fun upsert(row: RawatibLogEntity)

    @Query("UPDATE rawatib_tracking SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM rawatib_tracking WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM rawatib_tracking WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM rawatib_tracking WHERE deleted = 0 AND date = :date")
    fun observeDay(date: String): Flow<List<RawatibLogEntity>>

    @Query("SELECT * FROM rawatib_tracking WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<RawatibLogEntity>>

    @Query("SELECT * FROM rawatib_tracking WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date")
    suspend fun range(from: String, to: String): List<RawatibLogEntity>
}

@Dao
interface CustomSunnahDao : SyncDao<CustomSunnahEntity> {
    @Query("SELECT * FROM custom_sunnah WHERE dirty = 1")
    override suspend fun dirty(): List<CustomSunnahEntity>

    @Query("SELECT * FROM custom_sunnah WHERE id = :id")
    override suspend fun byId(id: String): CustomSunnahEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<CustomSunnahEntity>)

    @Upsert
    suspend fun upsert(row: CustomSunnahEntity)

    @Query("UPDATE custom_sunnah SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM custom_sunnah WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM custom_sunnah WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM custom_sunnah WHERE deleted = 0 ORDER BY createdAt")
    fun observeAll(): Flow<List<CustomSunnahEntity>>
}

@Dao
interface TaskDao : SyncDao<TaskEntity> {
    @Query("SELECT * FROM daily_tasks WHERE dirty = 1")
    override suspend fun dirty(): List<TaskEntity>

    @Query("SELECT * FROM daily_tasks WHERE id = :id")
    override suspend fun byId(id: String): TaskEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<TaskEntity>)

    @Upsert
    suspend fun upsert(row: TaskEntity)

    @Query("UPDATE daily_tasks SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM daily_tasks WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM daily_tasks WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM daily_tasks WHERE deleted = 0 AND archived = 0 ORDER BY sortOrder, createdAt")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM daily_tasks WHERE deleted = 0 AND archived = 0 ORDER BY sortOrder, createdAt")
    suspend fun active(): List<TaskEntity>

    @Query("SELECT * FROM daily_tasks WHERE deleted = 0 ORDER BY sortOrder, createdAt")
    fun observeAllIncludingArchived(): Flow<List<TaskEntity>>
}

@Dao
interface TaskCompletionDao : SyncDao<TaskCompletionEntity> {
    @Query("SELECT * FROM task_completions WHERE dirty = 1")
    override suspend fun dirty(): List<TaskCompletionEntity>

    @Query("SELECT * FROM task_completions WHERE id = :id")
    override suspend fun byId(id: String): TaskCompletionEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<TaskCompletionEntity>)

    @Upsert
    suspend fun upsert(row: TaskCompletionEntity)

    @Query("UPDATE task_completions SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM task_completions WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM task_completions WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM task_completions WHERE deleted = 0 AND date = :date")
    fun observeDay(date: String): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE deleted = 0 AND date BETWEEN :from AND :to")
    fun observeRange(from: String, to: String): Flow<List<TaskCompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE deleted = 0 AND date BETWEEN :from AND :to")
    suspend fun range(from: String, to: String): List<TaskCompletionEntity>
}

@Dao
interface CalendarEventDao : SyncDao<CalendarEventEntity> {
    @Query("SELECT * FROM calendar_events WHERE dirty = 1")
    override suspend fun dirty(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    override suspend fun byId(id: String): CalendarEventEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<CalendarEventEntity>)

    @Upsert
    suspend fun upsert(row: CalendarEventEntity)

    @Query("UPDATE calendar_events SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM calendar_events WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM calendar_events WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM calendar_events WHERE deleted = 0 ORDER BY date")
    fun observeAll(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date, timeOfDay")
    fun observeRange(from: String, to: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE deleted = 0 AND date >= :from ORDER BY date, timeOfDay")
    suspend fun upcoming(from: String): List<CalendarEventEntity>
}

@Dao
interface SettingDao : SyncDao<SettingEntity> {
    @Query("SELECT * FROM settings WHERE dirty = 1")
    override suspend fun dirty(): List<SettingEntity>

    @Query("SELECT * FROM settings WHERE id = :id")
    override suspend fun byId(id: String): SettingEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<SettingEntity>)

    @Upsert
    suspend fun upsert(row: SettingEntity)

    @Query("UPDATE settings SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM settings WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM settings WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM settings WHERE deleted = 0")
    fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings WHERE deleted = 0 AND scope = :scope")
    fun observeScope(scope: String): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings WHERE id = :id AND deleted = 0")
    fun observe(id: String): Flow<SettingEntity?>
}

@Dao
interface DailySummaryDao : SyncDao<DailySummaryEntity> {
    @Query("SELECT * FROM achievement_statistics WHERE dirty = 1")
    override suspend fun dirty(): List<DailySummaryEntity>

    @Query("SELECT * FROM achievement_statistics WHERE id = :id")
    override suspend fun byId(id: String): DailySummaryEntity?

    @Upsert
    override suspend fun upsertAll(rows: List<DailySummaryEntity>)

    @Upsert
    suspend fun upsert(row: DailySummaryEntity)

    @Query("UPDATE achievement_statistics SET dirty = 0 WHERE id = :id AND opId = :opId")
    override suspend fun markClean(id: String, opId: String)

    @Query("DELETE FROM achievement_statistics WHERE deleted = 1 AND dirty = 0 AND updatedAt < :before")
    override suspend fun purgeDeleted(before: Long)

    @Query("SELECT COUNT(*) FROM achievement_statistics WHERE dirty = 1")
    suspend fun dirtyCount(): Int

    @Query("SELECT * FROM achievement_statistics WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date")
    fun observeRange(from: String, to: String): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM achievement_statistics WHERE deleted = 0 AND date BETWEEN :from AND :to ORDER BY date")
    suspend fun range(from: String, to: String): List<DailySummaryEntity>
}
