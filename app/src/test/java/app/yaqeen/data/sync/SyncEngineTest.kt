package app.yaqeen.data.sync

import app.yaqeen.core.HybridClock
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.SyncDao
import app.yaqeen.data.local.SyncMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.Closeable

// ---------- بدائل الاختبار ----------

/** DAO في الذاكرة بنفس سلوك Room (upsert بالمفتاح، markClean بشرط opId). */
private class FakeDao : SyncDao<PrayerLogEntity> {
    val rows = LinkedHashMap<String, PrayerLogEntity>()
    override suspend fun dirty() = rows.values.filter { it.dirty }
    override suspend fun byId(id: String) = rows[id]
    override suspend fun upsertAll(rows: List<PrayerLogEntity>) { rows.forEach { this.rows[it.id] = it } }
    override suspend fun markClean(id: String, opId: String) {
        rows[id]?.let { if (it.opId == opId) rows[id] = it.copy(dirty = false) }
    }
    override suspend fun purgeDeleted(before: Long) {
        rows.values.removeAll { it.deleted && !it.dirty && it.updatedAt < before }
    }
}

private class MemCheckpoints : CheckpointStore {
    val map = HashMap<String, Long>()
    override suspend fun get(uid: String, collection: String) = map["$uid/$collection"] ?: 0L
    override suspend fun set(uid: String, collection: String, value: Long) { map["$uid/$collection"] = value }
}

/** "سحابة" مشتركة بين الأجهزة، تفرض قاعدة الأمان نفسها: لا يُقبل updatedAt أقدم من المخزَّن. */
private class FakeRemote : RemoteStore {
    val docs = HashMap<String, MutableMap<String, Map<String, Any?>>>() // collection -> id -> data
    var pushCount = 0
    var failPushes = false
    private val listeners = ArrayList<Triple<String, Long, (List<RemoteDoc>) -> Unit>>()

    private fun key(uid: String, c: String) = "$uid/$c"

    override suspend fun push(uid: String, collection: String, docId: String, data: Map<String, Any?>): PushOutcome {
        if (failPushes) return PushOutcome.FAILED
        val col = docs.getOrPut(key(uid, collection)) { HashMap() }
        val existing = col[docId]
        val newTs = (data["updatedAt"] as Number).toLong()
        if (existing != null && (existing["updatedAt"] as Number).toLong() > newTs) return PushOutcome.STALE_REJECTED
        require(data["uid"] == uid) { "uid mismatch" }
        col[docId] = data
        pushCount++
        listeners.filter { it.first == key(uid, collection) && newTs > it.second }.forEach { it.third(listOf(RemoteDoc(docId, data))) }
        return PushOutcome.OK
    }

    override suspend fun fetchSince(uid: String, collection: String, sinceUpdatedAt: Long): List<RemoteDoc> =
        docs[key(uid, collection)].orEmpty().filter { (it.value["updatedAt"] as Number).toLong() > sinceUpdatedAt }
            .map { RemoteDoc(it.key, it.value) }

    override fun listen(uid: String, collection: String, sinceUpdatedAt: Long, onDocs: (List<RemoteDoc>) -> Unit, onError: (Throwable) -> Unit): Closeable {
        val entry = Triple(key(uid, collection), sinceUpdatedAt, onDocs)
        listeners += entry
        return Closeable { listeners.remove(entry) }
    }

    override suspend fun writeProfile(uid: String, profile: Map<String, Any?>) {}
}

@OptIn(ExperimentalCoroutinesApi::class)
private class Device(val name: String, val remote: FakeRemote, scope: CoroutineScope, online: () -> Boolean = { true }, startClock: Long = 1_000) {
    val dao = FakeDao()
    private val ckpt = MemCheckpoints()
    val clock = TestClock(startClock)
    val hybrid = HybridClock(clock)
    var isOnline = true
    val table = RoomSyncTable("prayers_tracking", PrayerLogEntity.serializer(), dao)
    val sync = SyncManager(listOf(table), remote, ckpt, hybrid, { isOnline && online() }, scope, nowMillis = { clock.nowMillis() }, pushDebounceMs = 0)

    /** كتابة محلية كما يفعل SyncWriter: ختم + dirty. */
    suspend fun write(id: String, status: String, deleted: Boolean = false) {
        val meta = SyncMeta(hybrid.next(), name, "op-${name}-${hybrid.next()}")
        val base = dao.rows[id] ?: PrayerLogEntity(id = id, date = id.substringBefore('_'), prayer = id.substringAfter('_'), status = status)
        dao.rows[id] = base.copy(status = status).withMeta(meta, deleted = deleted, dirty = true)
    }

    fun row(id: String) = dao.rows[id]
}

private class TestClock(var now: Long) : app.yaqeen.core.Clock {
    override fun nowMillis() = now
    override fun zone() = java.time.ZoneId.of("UTC")
}

@OptIn(ExperimentalCoroutinesApi::class)
class SyncEngineTest {
    private val uid = "user-1"
    private val id = "2026-11-04_FAJR"

    private fun TestScope.devices(): Triple<FakeRemote, Device, Device> {
        val remote = FakeRemote()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        return Triple(remote, Device("A", remote, scope), Device("B", remote, scope))
    }

    @Test fun writeOnOneDeviceAppearsOnTheOther() = runTest {
        val (remote, a, b) = devices()
        a.sync.setUser(uid); b.sync.setUser(uid); advanceUntilIdle()
        a.write(id, "MOSQUE")
        a.sync.syncNow()
        b.sync.syncNow()
        assertEquals("MOSQUE", b.row(id)?.status)
        assertFalse("الصف المستلم ليس dirty", b.row(id)!!.dirty)
        assertFalse("بعد التأكيد لا يبقى dirty", a.row(id)!!.dirty)
        assertEquals(1, remote.pushCount)
    }

    @Test fun offlineWritesAreKeptAndSentWhenConnectionReturns() = runTest {
        val (remote, a, b) = devices()
        a.sync.setUser(uid); b.sync.setUser(uid); advanceUntilIdle()
        a.isOnline = false
        a.write(id, "HOME")
        assertFalse(a.sync.syncNow())
        assertEquals(0, remote.pushCount)
        assertTrue("التعديل يبقى محفوظًا محليًا وdirty", a.row(id)!!.dirty)
        a.isOnline = true
        assertTrue(a.sync.syncNow())
        assertEquals(1, remote.pushCount)
        b.sync.syncNow()
        assertEquals("HOME", b.row(id)?.status)
    }

    @Test fun failedPushNeverLosesDataAndRetrySucceeds() = runTest {
        val (remote, a, _) = devices()
        a.sync.setUser(uid); advanceUntilIdle()
        remote.failPushes = true
        a.write(id, "MOSQUE")
        a.sync.syncNow()
        assertTrue(a.row(id)!!.dirty)
        remote.failPushes = false
        a.sync.syncNow()
        assertFalse(a.row(id)!!.dirty)
        assertEquals("MOSQUE", remote.docs["$uid/prayers_tracking"]!![id]!!["status"])
    }

    @Test fun conflictingEditsConvergeToTheLatestWriter() = runTest {
        val (_, a, b) = devices()
        a.sync.setUser(uid); b.sync.setUser(uid); advanceUntilIdle()
        a.write(id, "HOME"); a.sync.syncNow(); b.sync.syncNow()
        // الجهازان يعدّلان السجل نفسه دون اتصال، وتعديل B أحدث
        a.isOnline = false; b.isOnline = false
        a.clock.now = 10_000; a.write(id, "MOSQUE")
        b.clock.now = 20_000; b.write(id, "NOT_PRAYED")
        a.isOnline = true; b.isOnline = true
        a.sync.syncNow()   // يرسل MOSQUE
        b.sync.syncNow()   // يسحب MOSQUE (أقدم) ثم يرسل NOT_PRAYED (الأحدث)
        a.sync.syncNow()
        assertEquals("NOT_PRAYED", a.row(id)?.status)
        assertEquals("NOT_PRAYED", b.row(id)?.status)
    }

    @Test fun serverRejectsStaleWriteAndDeviceAdoptsRemoteVersion() = runTest {
        val (remote, a, b) = devices()
        a.sync.setUser(uid); b.sync.setUser(uid); advanceUntilIdle()
        b.clock.now = 50_000; b.write(id, "MOSQUE"); b.sync.syncNow()
        // A يعدّل بتاريخ أقدم من النسخة البعيدة
        a.clock.now = 5_000; a.write(id, "HOME")
        a.sync.pushPending() // إرسال دون سحب مسبق: الخادم يرفض الأقدم فيسحب الجهاز النسخة الأحدث
        assertEquals("MOSQUE", a.row(id)?.status)
        assertFalse(a.row(id)!!.dirty)
        assertEquals("MOSQUE", remote.docs["$uid/prayers_tracking"]!![id]!!["status"])
    }

    @Test fun equalTimestampsAreResolvedDeterministicallyByDeviceId() = runTest {
        val remote = FakeRemote()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val a = Device("A", remote, scope, startClock = 7_000)
        val b = Device("B", remote, scope, startClock = 7_000)
        a.sync.setUser(uid); b.sync.setUser(uid); advanceUntilIdle()
        // نفس لحظة التعديل بالضبط على الجهازين
        a.dao.rows[id] = PrayerLogEntity(id, "2026-11-04", "FAJR", "HOME", updatedAt = 9_000, deviceId = "A", opId = "a1", dirty = true)
        b.dao.rows[id] = PrayerLogEntity(id, "2026-11-04", "FAJR", "MOSQUE", updatedAt = 9_000, deviceId = "B", opId = "b1", dirty = true)
        a.sync.syncNow(); b.sync.syncNow(); a.sync.syncNow(); b.sync.syncNow()
        // "B" > "A" فيفوز B على الجهازين
        assertEquals("MOSQUE", a.row(id)?.status)
        assertEquals("MOSQUE", b.row(id)?.status)
    }

    @Test fun resendingTheSameOperationDoesNotDuplicateOrChange() = runTest {
        val (remote, a, b) = devices()
        a.sync.setUser(uid); b.sync.setUser(uid); advanceUntilIdle()
        a.write(id, "HOME"); a.sync.syncNow(); b.sync.syncNow()
        val before = b.row(id)
        // إعادة تسليم الوثيقة نفسها (مثلًا من المستمع والسحب معًا)
        val doc = remote.docs["$uid/prayers_tracking"]!![id]!!
        val res = b.table.applyRemote(listOf(RemoteDoc(id, doc), RemoteDoc(id, doc)))
        assertEquals(0, res.applied)
        assertEquals(before, b.row(id))
        assertEquals(1, remote.docs["$uid/prayers_tracking"]!!.size)
    }

    @Test fun deleteIsPropagatedAsTombstone() = runTest {
        val (_, a, b) = devices()
        a.sync.setUser(uid); b.sync.setUser(uid); advanceUntilIdle()
        a.write(id, "HOME"); a.sync.syncNow(); b.sync.syncNow()
        a.clock.now = 30_000; a.write(id, "HOME", deleted = true)
        a.sync.syncNow(); b.sync.syncNow()
        assertTrue(b.row(id)!!.deleted)
    }

    @Test fun editDuringPushStaysDirtyBecauseOpIdChanged() = runTest {
        val (_, a, _) = devices()
        a.sync.setUser(uid); advanceUntilIdle()
        a.write(id, "HOME")
        val pending = a.table.pendingDocs().single()
        a.write(id, "MOSQUE")                 // تعديل جديد بعد التقاط الوثيقة وقبل التأكيد
        a.table.markClean(pending.id, pending.opId)
        assertTrue("التعديل الأحدث يجب ألا يُعلَّم نظيفًا", a.row(id)!!.dirty)
        assertEquals("MOSQUE", a.row(id)!!.status)
    }

    @Test fun checkpointAdvancesSoLaterSyncsFetchOnlyNewDocs() = runTest {
        val (remote, a, b) = devices()
        a.sync.setUser(uid); b.sync.setUser(uid); advanceUntilIdle()
        a.write(id, "HOME"); a.sync.syncNow(); b.sync.syncNow()
        val c = remote.fetchSince(uid, "prayers_tracking", b.row(id)!!.updatedAt)
        assertTrue("لا شيء جديد بعد نقطة المتابعة", c.isEmpty())
    }

    @Test fun nothingIsSentWhenSignedOut() = runTest {
        val (remote, a, _) = devices()
        a.write(id, "HOME")
        assertFalse(a.sync.syncNow())
        a.sync.requestPush(); advanceUntilIdle()
        assertEquals(0, remote.pushCount)
        assertTrue(a.row(id)!!.dirty)
    }

    @Test fun hybridClockIsStrictlyMonotonicEvenIfSystemClockGoesBack() {
        val clock = TestClock(10_000)
        val h = HybridClock(clock)
        val first = h.next()
        clock.now = 5_000 // الساعة رجعت للوراء
        val second = h.next()
        val third = h.next()
        assertTrue(second > first && third > second)
        h.observe(99_999)
        assertTrue(h.next() > 99_999)
    }

    @Test fun remoteWinsRule() {
        assertTrue(remoteWins(2, "A", 1, "Z"))
        assertFalse(remoteWins(1, "Z", 2, "A"))
        assertTrue(remoteWins(5, "B", 5, "A"))
        assertFalse(remoteWins(5, "A", 5, "B"))
        assertNull(null)
    }
}
