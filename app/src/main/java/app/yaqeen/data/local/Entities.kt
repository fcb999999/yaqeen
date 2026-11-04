package app.yaqeen.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

/** بيانات وصفية لكل تعديل: تُختم بها كل كتابة قابلة للمزامنة. */
data class SyncMeta(val updatedAt: Long, val deviceId: String, val opId: String)

/**
 * كل جدول قابل للمزامنة يطبّق هذه الواجهة.
 *  - id: معرّف ثابت (حتمي حيث أمكن مثل "2026-11-04_FAJR") لضمان أن إعادة المزامنة لا تكرر السجل.
 *  - updatedAt: رقم من HybridClock، يُستعمل لحل التعارض (الأحدث يغلب، وعند التساوي الأكبر deviceId).
 *  - deleted: شاهد حذف (tombstone) حتى يصل الحذف إلى بقية الأجهزة.
 *  - dirty: يوجد تعديل محلي لم يصل إلى السحابة بعد (لا يُرسل إلى Firestore).
 *  - opId: معرّف فريد للعملية الأخيرة؛ يمنع إعادة تطبيق العملية نفسها، ويمنع تعليم سجل بأنه نظيف إذا تغيّر أثناء الإرسال.
 */
interface SyncRow<T> {
    val id: String
    val updatedAt: Long
    val deleted: Boolean
    val dirty: Boolean
    val opId: String
    val deviceId: String
    fun withMeta(meta: SyncMeta, deleted: Boolean = this.deleted, dirty: Boolean = true): T
    fun asClean(): T
}

// ---------------------------------------------------------------- القرآن

/** آخر موضع قراءة. لكل جهاز سجل خاص به (id = deviceId) فلا يوجد تعارض كتابة أبدًا؛ والأحدث lastReadAt هو "الموضع الحالي". */
@Serializable
@Entity(tableName = "reading_progress")
data class ReadingProgressEntity(
    @PrimaryKey override val id: String,
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val juz: Int,
    val lastReadAt: Long,
    val deviceName: String = "",
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<ReadingProgressEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

@Serializable
@Entity(tableName = "quran_bookmark_categories")
data class BookmarkCategoryEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val color: Int,
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<BookmarkCategoryEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

@Serializable
@Entity(tableName = "quran_bookmarks", indices = [Index("surah", "ayah"), Index("categoryId")])
data class BookmarkEntity(
    @PrimaryKey override val id: String,
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val title: String = "",
    val note: String = "",
    /** لون العلامة ARGB. */
    val color: Int,
    val categoryId: String? = null,
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<BookmarkEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/**
 * ورد قرآني. المدى مخزَّن دائمًا بأرقام الآيات العامة (1..6236) أيًا كانت وحدة العرض.
 * progressAyahId = آخر آية أُنجزت في الورد (startAyahId-1 عند البداية).
 */
@Serializable
@Entity(tableName = "quran_plans")
data class QuranPlanEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val startDate: String,
    val endDate: String? = null,
    /** QuranPlanUnit.name */
    val unit: String,
    val startAyahId: Int,
    val endAyahId: Int,
    /** المقدار اليومي بوحدة الورد. */
    val dailyAmount: Int,
    val progressAyahId: Int,
    val reminderEnabled: Boolean = false,
    /** HH:mm */
    val reminderTime: String? = null,
    /** QuranPlanStatus.name */
    val status: String = "ACTIVE",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<QuranPlanEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/** سجل يومي للورد (id = "{planId}_{date}"). */
@Serializable
@Entity(tableName = "quran_plan_logs", indices = [Index("planId"), Index("date")])
data class QuranPlanLogEntity(
    @PrimaryKey override val id: String,
    val planId: String,
    val date: String,
    val ayahsRead: Int = 0,
    val pagesRead: Int = 0,
    /** هل بلغ المقدار اليومي المطلوب في ذلك اليوم. */
    val targetMet: Boolean = false,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<QuranPlanLogEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/** إجمالي القراءة في اليوم بغض النظر عن الورد (id = date). */
@Serializable
@Entity(tableName = "quran_day_logs")
data class QuranDayLogEntity(
    @PrimaryKey override val id: String,
    val date: String,
    val pagesRead: Int = 0,
    val ayahsRead: Int = 0,
    val seconds: Int = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<QuranDayLogEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

// ---------------------------------------------------------------- الأذكار

/** تقدم ذكر واحد في يوم واحد (id = "{date}_{itemId}"). */
@Serializable
@Entity(tableName = "adhkar_progress", indices = [Index("date"), Index("categoryId")])
data class AdhkarProgressEntity(
    @PrimaryKey override val id: String,
    val date: String,
    val categoryId: String,
    val itemId: String,
    val count: Int = 0,
    val target: Int = 1,
    val completed: Boolean = false,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<AdhkarProgressEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/** مفضلة/علامة مرجعية لذكر (id = "{type}_{itemId}"). */
@Serializable
@Entity(tableName = "adhkar_favorites")
data class AdhkarMarkEntity(
    @PrimaryKey override val id: String,
    val itemId: String,
    val categoryId: String,
    /** AdhkarMarkType.name */
    val type: String,
    val note: String = "",
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<AdhkarMarkEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

// ---------------------------------------------------------------- الصلاة والسنن

/** تسجيل صلاة (id = "{date}_{PRAYER}"). غياب السجل أو deleted=true يعني "غير مسجلة". */
@Serializable
@Entity(tableName = "prayers_tracking", indices = [Index("date")])
data class PrayerLogEntity(
    @PrimaryKey override val id: String,
    val date: String,
    /** Prayer.name */
    val prayer: String,
    /** PrayerStatus.name */
    val status: String,
    val loggedAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<PrayerLogEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/** تسجيل سنة راتبة (id = "{date}_{sunnahId}"). */
@Serializable
@Entity(tableName = "rawatib_tracking", indices = [Index("date")])
data class RawatibLogEntity(
    @PrimaryKey override val id: String,
    val date: String,
    /** Sunnah.name أو "custom:{id}" */
    val sunnahId: String,
    val done: Boolean = true,
    val rakaat: Int = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<RawatibLogEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/** سنة مخصصة يضيفها المستخدم. */
@Serializable
@Entity(tableName = "custom_sunnah")
data class CustomSunnahEntity(
    @PrimaryKey override val id: String,
    val name: String,
    val rakaat: Int,
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<CustomSunnahEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

// ---------------------------------------------------------------- المهام والتقويم

@Serializable
@Entity(tableName = "daily_tasks")
data class TaskEntity(
    @PrimaryKey override val id: String,
    val title: String,
    val notes: String = "",
    /** HH:mm أو null */
    val timeOfDay: String? = null,
    /** TaskPriority.ordinal */
    val priority: Int = 1,
    /** TaskRepeat.name */
    val repeat: String = "DAILY",
    /** أيام الأسبوع: بت لكل يوم ISO (الاثنين=1<<0 ... الأحد=1<<6). 127 = كل الأيام. */
    val daysMask: Int = 127,
    /** TaskLink.name */
    val link: String = "NONE",
    /** معرّف إضافي للربط (مثل planId). */
    val linkRefId: String = "",
    val startDate: String,
    val endDate: String? = null,
    val reminderEnabled: Boolean = false,
    val archived: Boolean = false,
    /** مفتاح المهمة الجاهزة التي أُنشئت منها (للتمييز)، فارغ للمهام الشخصية. */
    val preset: String = "",
    val sortOrder: Int = 0,
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<TaskEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/** إنجاز يدوي لمهمة غير مرتبطة في يوم (id = "{taskId}_{date}"). */
@Serializable
@Entity(tableName = "task_completions", indices = [Index("date"), Index("taskId")])
data class TaskCompletionEntity(
    @PrimaryKey override val id: String,
    val taskId: String,
    val date: String,
    val completedAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<TaskCompletionEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/** حدث شخصي في التقويم (المناسبات الإسلامية تُحسب ولا تُخزَّن). */
@Serializable
@Entity(tableName = "calendar_events", indices = [Index("date")])
data class CalendarEventEntity(
    @PrimaryKey override val id: String,
    val title: String,
    val notes: String = "",
    /** تاريخ ميلادي ISO */
    val date: String,
    val timeOfDay: String? = null,
    val reminderEnabled: Boolean = false,
    val createdAt: Long = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<CalendarEventEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

// ---------------------------------------------------------------- الإعدادات والإحصاءات

/** إعداد (id = "{scope}:{key}"). scope=user → user_settings، scope=notification → notification_settings. */
@Serializable
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey override val id: String,
    val scope: String,
    val key: String,
    val value: String,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<SettingEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}

/**
 * ملخص يومي مُشتق من السجلات الأصلية (id = date). هو مخزن مؤقت للإحصائيات يُحسب محليًا
 * ويُزامَن ليرى المستخدم إحصاءاته على كل أجهزته. السجلات الأصلية هي مصدر الحقيقة.
 * القاعدة: القيم "المسجلة" فقط؛ ما لم يُسجَّل لا يُعدّ تقصيرًا.
 */
@Serializable
@Entity(tableName = "achievement_statistics")
data class DailySummaryEntity(
    @PrimaryKey override val id: String,
    val date: String,
    val prayersRecorded: Int = 0,
    val prayersMosque: Int = 0,
    val prayersHome: Int = 0,
    val prayersNotPrayed: Int = 0,
    val rawatibDone: Int = 0,
    val rawatibRakaat: Int = 0,
    val adhkarMorningDone: Boolean = false,
    val adhkarEveningDone: Boolean = false,
    val adhkarSleepDone: Boolean = false,
    val adhkarItemsCompleted: Int = 0,
    val quranPages: Int = 0,
    val quranAyahs: Int = 0,
    val quranPlanTargetMet: Boolean = false,
    val tasksTotal: Int = 0,
    val tasksDone: Int = 0,
    override val updatedAt: Long = 0,
    override val deleted: Boolean = false,
    @Transient override val dirty: Boolean = false,
    override val opId: String = "",
    override val deviceId: String = "",
) : SyncRow<DailySummaryEntity> {
    override fun withMeta(meta: SyncMeta, deleted: Boolean, dirty: Boolean) =
        copy(updatedAt = meta.updatedAt, deviceId = meta.deviceId, opId = meta.opId, deleted = deleted, dirty = dirty)
    override fun asClean() = copy(dirty = false)
}
