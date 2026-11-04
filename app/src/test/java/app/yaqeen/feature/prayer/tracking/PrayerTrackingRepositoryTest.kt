package app.yaqeen.feature.prayer.tracking

import app.yaqeen.core.Clock
import app.yaqeen.core.HybridClock
import app.yaqeen.data.local.CustomSunnahDao
import app.yaqeen.data.local.CustomSunnahEntity
import app.yaqeen.data.local.PrayerLogDao
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.RawatibLogDao
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.data.local.SettingDao
import app.yaqeen.data.local.SettingEntity
import app.yaqeen.data.local.SyncRow
import app.yaqeen.data.settings.SettingsRepository
import app.yaqeen.data.sync.Stamper
import app.yaqeen.data.sync.SyncWriter
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.Sunnah
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** تخزين في الذاكرة يحاكي سلوك Room (upsert بالمعرّف) لاختبار منطق المستودع بلا Android. */
private class Store<T : SyncRow<T>> {
    val rows = MutableStateFlow<Map<String, T>>(emptyMap())
    fun all(): List<T> = rows.value.values.toList()
    fun upsert(list: List<T>) { rows.value = rows.value + list.associateBy { it.id } }
    fun live(): Flow<List<T>> = rows.map { m -> m.values.filter { !it.deleted } }
}

private class FakePrayerDao : PrayerLogDao {
    val store = Store<PrayerLogEntity>()
    override suspend fun dirty() = store.all().filter { it.dirty }
    override suspend fun byId(id: String) = store.rows.value[id]
    override suspend fun upsertAll(rows: List<PrayerLogEntity>) = store.upsert(rows)
    override suspend fun upsert(row: PrayerLogEntity) = store.upsert(listOf(row))
    override suspend fun markClean(id: String, opId: String) = Unit
    override suspend fun purgeDeleted(before: Long) = Unit
    override suspend fun dirtyCount() = dirty().size
    override fun observeDay(date: String) = store.live().map { l -> l.filter { it.date == date } }
    override fun observeRange(from: String, to: String) = store.live().map { l -> l.filter { it.date in from..to }.sortedBy { it.date } }
    override suspend fun range(from: String, to: String) = observeRange(from, to).first()
}

private class FakeRawatibDao : RawatibLogDao {
    val store = Store<RawatibLogEntity>()
    override suspend fun dirty() = store.all().filter { it.dirty }
    override suspend fun byId(id: String) = store.rows.value[id]
    override suspend fun upsertAll(rows: List<RawatibLogEntity>) = store.upsert(rows)
    override suspend fun upsert(row: RawatibLogEntity) = store.upsert(listOf(row))
    override suspend fun markClean(id: String, opId: String) = Unit
    override suspend fun purgeDeleted(before: Long) = Unit
    override suspend fun dirtyCount() = dirty().size
    override fun observeDay(date: String) = store.live().map { l -> l.filter { it.date == date } }
    override fun observeRange(from: String, to: String) = store.live().map { l -> l.filter { it.date in from..to } }
    override suspend fun range(from: String, to: String) = observeRange(from, to).first()
}

private class FakeCustomDao : CustomSunnahDao {
    val store = Store<CustomSunnahEntity>()
    override suspend fun dirty() = store.all().filter { it.dirty }
    override suspend fun byId(id: String) = store.rows.value[id]
    override suspend fun upsertAll(rows: List<CustomSunnahEntity>) = store.upsert(rows)
    override suspend fun upsert(row: CustomSunnahEntity) = store.upsert(listOf(row))
    override suspend fun markClean(id: String, opId: String) = Unit
    override suspend fun purgeDeleted(before: Long) = Unit
    override suspend fun dirtyCount() = dirty().size
    override fun observeAll() = store.live().map { l -> l.sortedBy { it.createdAt } }
}

private class FakeSettingDao : SettingDao {
    val store = Store<SettingEntity>()
    override suspend fun dirty() = store.all().filter { it.dirty }
    override suspend fun byId(id: String) = store.rows.value[id]
    override suspend fun upsertAll(rows: List<SettingEntity>) = store.upsert(rows)
    override suspend fun upsert(row: SettingEntity) = store.upsert(listOf(row))
    override suspend fun markClean(id: String, opId: String) = Unit
    override suspend fun purgeDeleted(before: Long) = Unit
    override suspend fun dirtyCount() = dirty().size
    override fun observeAll() = store.live()
    override fun observeScope(scope: String) = store.live().map { l -> l.filter { it.scope == scope } }
    override fun observe(id: String) = store.rows.map { m -> m[id]?.takeIf { !it.deleted } }
}

class PrayerTrackingRepositoryTest {
    private val today = LocalDate.of(2026, 11, 4)
    private val yesterday = today.minusDays(1)

    private class FixedClock : Clock {
        var ms = 1_000_000L
        override fun nowMillis() = ms
        override fun zone(): ZoneId = ZoneId.of("UTC")
    }

    private class Env {
        val prayers = FakePrayerDao()
        val rawatib = FakeRawatibDao()
        val customs = FakeCustomDao()
        val settingsDao = FakeSettingDao()
        val clock = FixedClock()
        val writer = SyncWriter(Stamper("device-1", HybridClock(clock)))
        val settings = SettingsRepository(settingsDao, writer)
        var seq = 0
    }

    private fun repo(env: Env, today: LocalDate = this.today) = PrayerTrackingRepository(
        prayerDao = env.prayers, rawatibDao = env.rawatib, customDao = env.customs,
        settings = env.settings, writer = env.writer,
        nowMillis = { env.clock.nowMillis() }, today = { today },
        idGenerator = { "id${++env.seq}" },
    )

    @Test fun setStatusWritesDeterministicIdAndMarksDirty() = runBlocking {
        val env = Env(); val r = repo(env)
        assertTrue(r.setStatus(today, Prayer.FAJR, PrayerStatus.MOSQUE))
        val row = env.prayers.store.rows.value["2026-11-04_FAJR"]
        assertNotNull(row)
        assertEquals("MOSQUE", row!!.status)
        assertEquals("2026-11-04", row.date)
        assertEquals("FAJR", row.prayer)
        assertTrue(row.dirty)
        assertFalse(row.deleted)
        assertEquals(1_000_000L, row.loggedAt)
        assertEquals("device-1", row.deviceId)
        assertTrue(row.opId.isNotEmpty())
        assertEquals(mapOf(Prayer.FAJR to PrayerStatus.MOSQUE), r.observeDay(today).first())
    }

    @Test fun changingStatusKeepsSingleRow() = runBlocking {
        val env = Env(); val r = repo(env)
        r.setStatus(today, Prayer.ASR, PrayerStatus.HOME)
        r.setStatus(today, Prayer.ASR, PrayerStatus.MOSQUE)
        assertEquals(1, env.prayers.store.all().size)
        assertEquals(PrayerStatus.MOSQUE, r.currentStatus(today, Prayer.ASR))
    }

    @Test fun tappingSameStatusAgainClearsWithTombstone() = runBlocking {
        val env = Env(); val r = repo(env)
        r.toggleStatus(today, Prayer.DHUHR, PrayerStatus.HOME)
        assertEquals(PrayerStatus.HOME, r.currentStatus(today, Prayer.DHUHR))
        r.toggleStatus(today, Prayer.DHUHR, PrayerStatus.HOME)
        assertNull(r.currentStatus(today, Prayer.DHUHR))
        val row = env.prayers.store.rows.value["2026-11-04_DHUHR"]!!
        assertTrue("يبقى شاهد حذف للمزامنة", row.deleted)
        assertTrue(row.dirty)
        assertTrue(r.observeDay(today).first().isEmpty())
    }

    @Test fun tappingDifferentStatusSwitches() = runBlocking {
        val env = Env(); val r = repo(env)
        r.toggleStatus(today, Prayer.ISHA, PrayerStatus.HOME)
        r.toggleStatus(today, Prayer.ISHA, PrayerStatus.NOT_PRAYED)
        assertEquals(PrayerStatus.NOT_PRAYED, r.currentStatus(today, Prayer.ISHA))
    }

    @Test fun recordingAgainAfterTombstoneRevivesRow() = runBlocking {
        val env = Env(); val r = repo(env)
        r.setStatus(today, Prayer.FAJR, PrayerStatus.HOME)
        r.setStatus(today, Prayer.FAJR, null)
        r.setStatus(today, Prayer.FAJR, PrayerStatus.MOSQUE)
        val row = env.prayers.store.rows.value["2026-11-04_FAJR"]!!
        assertFalse(row.deleted)
        assertEquals("MOSQUE", row.status)
        assertEquals(1, env.prayers.store.all().size)
    }

    @Test fun clearingNonExistingRecordIsNoOp() = runBlocking {
        val env = Env(); val r = repo(env)
        assertTrue(r.setStatus(today, Prayer.FAJR, null))
        assertTrue(env.prayers.store.all().isEmpty())
    }

    @Test fun pastDaysCanBeCorrectedButFutureCannot() = runBlocking {
        val env = Env(); val r = repo(env)
        assertTrue(r.setStatus(yesterday, Prayer.MAGHRIB, PrayerStatus.MOSQUE))
        assertTrue(r.canEdit(yesterday))
        assertFalse(r.canEdit(today.plusDays(1)))
        assertFalse(r.setStatus(today.plusDays(1), Prayer.FAJR, PrayerStatus.MOSQUE))
        assertFalse(r.toggleStatus(today.plusDays(1), Prayer.FAJR, PrayerStatus.MOSQUE))
        assertFalse(r.setSunnahDone(today.plusDays(1), SunnahModel.DEFAULTS.first(), true))
        assertEquals(1, env.prayers.store.all().size)
    }

    @Test fun differentDaysAreIndependent() = runBlocking {
        val env = Env(); val r = repo(env)
        r.setStatus(today, Prayer.FAJR, PrayerStatus.MOSQUE)
        r.setStatus(yesterday, Prayer.FAJR, PrayerStatus.HOME)
        assertEquals(PrayerStatus.MOSQUE, r.currentStatus(today, Prayer.FAJR))
        assertEquals(PrayerStatus.HOME, r.currentStatus(yesterday, Prayer.FAJR))
        val range = r.observePrayerRange(yesterday, today).first()
        assertEquals(2, range.size)
    }

    // ------------------------------------------------------------ السنن

    @Test fun sunnahDoneWritesRowWithRakaatAndClearsWithTombstone() = runBlocking {
        val env = Env(); val r = repo(env)
        val item = SunnahModel.DEFAULTS.first { it.id == Sunnah.DHUHR_BEFORE.name }
        r.setSunnahDone(today, item, true)
        val row = env.rawatib.store.rows.value["2026-11-04_DHUHR_BEFORE"]!!
        assertTrue(row.done)
        assertEquals(4, row.rakaat)
        val list = r.observeChecklist(today).first()
        assertEquals(1, list.doneCount)
        assertEquals(4, list.totalRakaat)
        assertEquals(5, list.totalCount)

        r.setSunnahDone(today, item, false)
        assertTrue(env.rawatib.store.rows.value["2026-11-04_DHUHR_BEFORE"]!!.deleted)
        assertEquals(0, r.observeChecklist(today).first().doneCount)
    }

    @Test fun customSunnahLifecycle() = runBlocking {
        val env = Env(); val r = repo(env)
        // إضافة
        assertNull(r.addCustomSunnah("  صلاة   الضحى ", 2))
        val entity = env.customs.store.all().single()
        assertEquals("صلاة الضحى", entity.name)
        assertEquals(2, entity.rakaat)
        assertEquals("id1", entity.id)
        // تظهر في القائمة بمعرّف custom:
        var list = r.observeChecklist(today).first()
        assertEquals(6, list.totalCount)
        val item = list.entries.last().item
        assertEquals("custom:id1", item.id)
        assertTrue(item.isCustom)
        // تسجيلها
        r.setSunnahDone(today, item, true)
        assertEquals("2026-11-04_custom:id1", env.rawatib.store.all().single().id)
        assertEquals(2, r.observeChecklist(today).first().totalRakaat)
        // تعديل الاسم والركعات
        assertNull(r.updateCustomSunnah("id1", "الضحى", 4))
        assertEquals("الضحى", env.customs.store.rows.value["id1"]!!.name)
        assertEquals(4, env.customs.store.rows.value["id1"]!!.rakaat)
        // السجل السابق يحتفظ بركعاته
        assertEquals(2, r.observeChecklist(today).first().entries.last().rakaat)
        // أرشفة: تختفي من الغد لكن تبقى في يوم لها فيه إنجاز
        r.setArchived("custom:id1", true)
        assertEquals(6, r.observeChecklist(today).first().totalCount)
        assertEquals(5, r.observeChecklist(yesterday).first().totalCount)
        assertTrue(r.observeCustomItems().first().single().archived)
        // إلغاء الأرشفة
        r.setArchived("custom:id1", false)
        assertEquals(6, r.observeChecklist(yesterday).first().totalCount)
        // حذف: تختفي، ويبقى سجل الإنجاز
        r.deleteCustomSunnah("id1")
        assertTrue(env.customs.store.rows.value["id1"]!!.deleted)
        assertTrue(env.customs.store.rows.value["id1"]!!.dirty)
        assertTrue(r.observeCustomItems().first().isEmpty())
        assertEquals(5, r.observeChecklist(today).first().totalCount.let { it })
        assertEquals(1, env.rawatib.store.all().size)
    }

    @Test fun customSunnahValidation() = runBlocking {
        val env = Env(); val r = repo(env)
        assertNotNull(r.addCustomSunnah("   ", 2))
        assertNotNull(r.addCustomSunnah("الوتر", 0))
        assertNotNull(r.addCustomSunnah("الوتر", 31))
        assertNotNull(r.addCustomSunnah("x".repeat(41), 2))
        assertNotNull("لا يكرر اسم سنة راتبة", r.addCustomSunnah("ركعتا الفجر", 2))
        assertTrue(env.customs.store.all().isEmpty())
        assertNull(r.addCustomSunnah("الوتر", 3))
        assertNotNull("لا يكرر اسم سنة مخصصة", r.addCustomSunnah("الوتر", 1))
        assertNotNull(r.updateCustomSunnah("missing", "اسم", 2))
        // تعديل نفسها بالاسم نفسه مسموح
        assertNull(r.updateCustomSunnah("id1", "الوتر", 5))
    }

    @Test fun sunnahModelHelpers() {
        assertEquals("a b", SunnahModel.cleanName("  a   b  "))
        assertEquals(setOf("custom:1", "custom:2"), SunnahModel.decodeIds("custom:1, custom:2,,"))
        assertEquals("custom:1,custom:2", SunnahModel.encodeIds(listOf("custom:2", "custom:1", "custom:1", " ")))
        assertEquals(emptySet<String>(), SunnahModel.decodeIds(""))
        assertEquals(5, SunnahModel.DEFAULTS.size)
        assertEquals(Sunnah.FAJR_BEFORE.name, SunnahModel.DEFAULTS.first().id)
    }
}
