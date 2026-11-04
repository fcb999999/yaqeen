package app.yaqeen.feature.prayer.tracking

import app.yaqeen.AppContainer
import app.yaqeen.core.newId
import app.yaqeen.data.local.CustomSunnahDao
import app.yaqeen.data.local.CustomSunnahEntity
import app.yaqeen.data.local.PrayerLogDao
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.RawatibLogDao
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.data.settings.SettingsRepository
import app.yaqeen.data.settings.StringKey
import app.yaqeen.data.sync.SyncWriter
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.SettingScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate

/** إعدادات متزامنة خاصة بتتبع الصلاة. */
object PrayerTrackingSettings {
    /** معرّفات السنن المخصصة المؤرشفة ("custom:<id>") مفصولة بفاصلة. */
    val ARCHIVED_SUNNAH = StringKey(SettingScope.USER, "prayer_archived_sunnah", "")
}

/**
 * مصدر واحد لقراءة وكتابة تسجيل الصلوات والسنن الرواتب (تستعمله شاشة الصلاة والإحصاءات و[PrayerQuickRow]).
 *
 *  - كل كتابة عبر [SyncWriter] فقط (تختم وتزامن)؛ المسح = حذف منطقي (tombstone).
 *  - لا تُسجَّل الأيام المستقبلية: تعيد دوال الكتابة false.
 *  - غياب السجل = غير مسجّلة (محايد وليس تركًا).
 */
class PrayerTrackingRepository(
    private val prayerDao: PrayerLogDao,
    private val rawatibDao: RawatibLogDao,
    private val customDao: CustomSunnahDao,
    private val settings: SettingsRepository,
    private val writer: SyncWriter,
    private val nowMillis: () -> Long,
    private val today: () -> LocalDate,
    private val idGenerator: () -> String = { newId() },
) {
    constructor(c: AppContainer) : this(
        prayerDao = c.db.prayerLogs(),
        rawatibDao = c.db.rawatibLogs(),
        customDao = c.db.customSunnah(),
        settings = c.settings,
        writer = c.writer,
        nowMillis = { c.clock.nowMillis() },
        today = { c.clock.today() },
    )

    /** يمنع تداخل نقرتين سريعتين (قراءة ثم كتابة) على السجل نفسه. */
    private val lock = Mutex()

    companion object {
        fun prayerLogId(date: LocalDate, prayer: Prayer) = "${date}_${prayer.name}"
        fun rawatibLogId(date: LocalDate, sunnahId: String) = "${date}_$sunnahId"
    }

    fun canEdit(date: LocalDate): Boolean = !date.isAfter(today())

    // ------------------------------------------------------------ الصلوات

    /** حالات صلوات اليوم؛ الصلاة غير المسجّلة غائبة من الخريطة. */
    fun observeDay(date: LocalDate): Flow<Map<Prayer, PrayerStatus>> =
        prayerDao.observeDay(date.toString()).map { rows -> rows.toStatusMap() }.distinctUntilChanged()

    fun observePrayerRange(from: LocalDate, to: LocalDate): Flow<List<PrayerLogEntity>> =
        prayerDao.observeRange(from.toString(), to.toString())

    private fun List<PrayerLogEntity>.toStatusMap(): Map<Prayer, PrayerStatus> {
        val out = LinkedHashMap<Prayer, PrayerStatus>()
        for (row in this.sortedBy { it.updatedAt }) {
            if (row.deleted) continue
            val p = Prayer.entries.firstOrNull { it.name == row.prayer } ?: continue
            val s = PrayerStatus.entries.firstOrNull { it.name == row.status } ?: continue
            out[p] = s
        }
        return Prayer.entries.mapNotNull { p -> out[p]?.let { p to it } }.toMap()
    }

    /** الحالة المسجّلة حاليًا أو null. */
    suspend fun currentStatus(date: LocalDate, prayer: Prayer): PrayerStatus? =
        prayerDao.byId(prayerLogId(date, prayer))?.takeIf { !it.deleted }
            ?.let { row -> PrayerStatus.entries.firstOrNull { it.name == row.status } }

    /** يضبط الحالة، أو يمسحها إن كانت [status] = null. يعيد false لليوم المستقبلي. */
    suspend fun setStatus(date: LocalDate, prayer: Prayer, status: PrayerStatus?): Boolean {
        if (!canEdit(date)) return false
        lock.withLock { write(date, prayer, status) }
        return true
    }

    /** نقر حالة: إن كانت هي المسجّلة تُمسح (حذف منطقي) وإلا تُسجَّل. يعيد false لليوم المستقبلي. */
    suspend fun toggleStatus(date: LocalDate, prayer: Prayer, tapped: PrayerStatus): Boolean {
        if (!canEdit(date)) return false
        lock.withLock {
            val current = currentStatus(date, prayer)
            write(date, prayer, if (current == tapped) null else tapped)
        }
        return true
    }

    private suspend fun write(date: LocalDate, prayer: Prayer, status: PrayerStatus?) {
        val id = prayerLogId(date, prayer)
        val existing = prayerDao.byId(id)?.takeIf { !it.deleted }
        if (status == null) {
            if (existing != null) writer.delete(prayerDao, existing)
            return
        }
        if (existing != null && existing.status == status.name) return
        writer.save(
            prayerDao,
            PrayerLogEntity(id = id, date = date.toString(), prayer = prayer.name, status = status.name, loggedAt = nowMillis()),
        )
    }

    // ------------------------------------------------------------ السنن

    private fun archivedFlow(): Flow<Set<String>> =
        settings.observe(PrayerTrackingSettings.ARCHIVED_SUNNAH).map { SunnahModel.decodeIds(it) }.distinctUntilChanged()

    fun observeRawatibRange(from: LocalDate, to: LocalDate): Flow<List<RawatibLogEntity>> =
        rawatibDao.observeRange(from.toString(), to.toString())

    /** قائمة اليوم (الخمس الافتراضية + المخصصة النشطة) بحالة الإنجاز. */
    fun observeChecklist(date: LocalDate): Flow<RawatibDay> =
        combine(customDao.observeAll(), archivedFlow(), rawatibDao.observeDay(date.toString())) { customs, archived, logs ->
            SunnahModel.buildChecklist(customs, archived, logs)
        }

    /** كل السنن المخصصة (حتى المؤرشفة) لشاشة الإدارة. */
    fun observeCustomItems(): Flow<List<SunnahItem>> =
        combine(customDao.observeAll(), archivedFlow()) { customs, archived ->
            customs.sortedBy { it.createdAt }.map { e ->
                SunnahModel.fromEntity(e, archived = SunnahModel.customId(e.id) in archived)
            }
        }

    /** أسماء السنن المخصصة حسب معرّفها "custom:<id>" (للإحصاءات). */
    fun observeCustomNames(): Flow<Map<String, String>> =
        customDao.observeAll().map { list -> list.associate { SunnahModel.customId(it.id) to it.name } }

    /** يضع/يمسح علامة إنجاز سنة في يوم. يعيد false لليوم المستقبلي. */
    suspend fun setSunnahDone(date: LocalDate, item: SunnahItem, done: Boolean): Boolean {
        if (!canEdit(date)) return false
        lock.withLock {
            val id = rawatibLogId(date, item.id)
            val existing = rawatibDao.byId(id)?.takeIf { !it.deleted }
            if (!done) {
                if (existing != null) writer.delete(rawatibDao, existing)
            } else if (existing == null || !existing.done) {
                writer.save(
                    rawatibDao,
                    RawatibLogEntity(id = id, date = date.toString(), sunnahId = item.id, done = true, rakaat = item.rakaat),
                )
            }
        }
        return true
    }

    // ------------------------------------------------------------ السنن المخصصة (إدارة)

    private suspend fun allNames(excludeEntityId: String? = null): List<String> {
        val customs = customDao.observeAll().first().filter { it.id != excludeEntityId }.map { it.name }
        return SunnahModel.DEFAULTS.map { it.name } + customs
    }

    /** يضيف سنة مخصصة. يعيد رسالة خطأ عربية أو null عند النجاح. */
    suspend fun addCustomSunnah(rawName: String, rakaat: Int): String? = lock.withLock {
        SunnahModel.validate(rawName, rakaat, allNames())?.let { return@withLock it }
        writer.save(
            customDao,
            CustomSunnahEntity(id = idGenerator(), name = SunnahModel.cleanName(rawName), rakaat = rakaat, createdAt = nowMillis()),
        )
        null
    }

    /** يعدّل اسم/ركعات سنة مخصصة (السجلات السابقة تحتفظ بعدد ركعاتها). */
    suspend fun updateCustomSunnah(entityId: String, rawName: String, rakaat: Int): String? = lock.withLock {
        val existing = customDao.byId(entityId)?.takeIf { !it.deleted } ?: return@withLock "لم تعد هذه السنة موجودة"
        SunnahModel.validate(rawName, rakaat, allNames(excludeEntityId = entityId))?.let { return@withLock it }
        val name = SunnahModel.cleanName(rawName)
        if (existing.name != name || existing.rakaat != rakaat) {
            writer.save(customDao, existing.copy(name = name, rakaat = rakaat))
        }
        null
    }

    /** أرشفة/إلغاء أرشفة: المؤرشفة تختفي من قائمة اليوم وتبقى سجلاتها السابقة في الإحصاءات. */
    suspend fun setArchived(itemId: String, archived: Boolean) {
        lock.withLock {
            val ids = SunnahModel.decodeIds(settings.get(PrayerTrackingSettings.ARCHIVED_SUNNAH)).toMutableSet()
            val changed = if (archived) ids.add(itemId) else ids.remove(itemId)
            if (changed) settings.set(PrayerTrackingSettings.ARCHIVED_SUNNAH, SunnahModel.encodeIds(ids))
        }
    }

    /** حذف سنة مخصصة (حذف منطقي). سجلاتها السابقة تبقى محفوظة وتُحسب في الإحصاءات. */
    suspend fun deleteCustomSunnah(entityId: String) {
        lock.withLock {
            val existing = customDao.byId(entityId)?.takeIf { !it.deleted } ?: return
            writer.delete(customDao, existing)
            val itemId = SunnahModel.customId(entityId)
            val ids = SunnahModel.decodeIds(settings.get(PrayerTrackingSettings.ARCHIVED_SUNNAH))
            if (itemId in ids) settings.set(PrayerTrackingSettings.ARCHIVED_SUNNAH, SunnahModel.encodeIds(ids - itemId))
        }
    }
}
