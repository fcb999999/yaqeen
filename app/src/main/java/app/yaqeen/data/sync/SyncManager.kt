package app.yaqeen.data.sync

import app.yaqeen.core.HybridClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.Closeable

/** نتيجة إرسال وثيقة واحدة. */
enum class PushOutcome {
    OK,

    /** رفض الخادم لأن النسخة البعيدة أحدث (قاعدة الأمان تفرض Last-Writer-Wins). سيحلّها السحب التالي. */
    STALE_REJECTED,

    /** فشل مؤقت (شبكة، خادم). تبقى الوثيقة dirty وتُعاد لاحقًا. */
    FAILED,
}

/** طبقة التخزين السحابي (Firestore في الإنتاج، وبديل في الذاكرة في الاختبارات). */
interface RemoteStore {
    suspend fun push(uid: String, collection: String, docId: String, data: Map<String, Any?>): PushOutcome
    suspend fun fetchSince(uid: String, collection: String, sinceUpdatedAt: Long): List<RemoteDoc>
    fun listen(
        uid: String,
        collection: String,
        sinceUpdatedAt: Long,
        onDocs: (List<RemoteDoc>) -> Unit,
        onError: (Throwable) -> Unit,
    ): Closeable

    suspend fun writeProfile(uid: String, profile: Map<String, Any?>)
}

/** يحفظ نقطة المتابعة (آخر updatedAt تم سحبه) لكل مستخدم ومجموعة. */
interface CheckpointStore {
    suspend fun get(uid: String, collection: String): Long
    suspend fun set(uid: String, collection: String, value: Long)
}

enum class SyncStatus { LOCAL_ONLY, IDLE, SYNCING, OFFLINE, ERROR }

data class SyncState(
    val status: SyncStatus = SyncStatus.LOCAL_ONLY,
    val lastSyncAt: Long = 0,
    val pending: Int = 0,
    val message: String? = null,
)

/**
 * محرك المزامنة (Local-First):
 *  1) كل كتابة تحدث في Room أولًا وتُوسَم dirty.
 *  2) عند توفر الإنترنت وحساب مسجّل تُرسل الوثائق dirty (وثيقة وثيقة؛ قواعد Firestore ترفض الأقدم).
 *  3) تُسحب تحديثات الأجهزة الأخرى بمستمع لحظي + سحب كامل عند البدء، وتُطبَّق بقاعدة الأحدث يغلب.
 *  4) لا شيء يضيع عند انقطاع الاتصال: dirty يبقى حتى يؤكد الخادم الاستلام.
 */
class SyncManager(
    private val tables: List<AnySyncTable>,
    private val remote: RemoteStore,
    private val checkpoints: CheckpointStore,
    private val hybridClock: HybridClock,
    private val isOnline: () -> Boolean,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val pushDebounceMs: Long = 800,
) {
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var uid: String? = null
    private val listeners = ArrayList<Closeable>()
    private var debounceJob: Job? = null

    /** يُستدعى عند تغيّر حالة تسجيل الدخول. uid=null → الوضع المحلي فقط. */
    fun setUser(newUid: String?) {
        if (newUid == uid) return
        stopListening()
        uid = newUid
        if (newUid == null) {
            _state.update { it.copy(status = SyncStatus.LOCAL_ONLY, message = null) }
        } else {
            _state.update { it.copy(status = SyncStatus.IDLE, message = null) }
            scope.launch { syncNow(); startListening() }
        }
    }

    /** يُستدعى بعد كل كتابة محلية: إرسال مؤجل قليلًا لتجميع التغييرات المتتالية. */
    fun requestPush() {
        if (uid == null) return
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(pushDebounceMs)
            pushPending()
        }
    }

    /** سحب ثم إرسال. آمن للاستدعاء المتزامن (يُسلسل بقفل). */
    suspend fun syncNow(): Boolean {
        val u = uid ?: return false
        if (!isOnline()) {
            _state.update { it.copy(status = SyncStatus.OFFLINE, pending = pendingCount()) }
            return false
        }
        return mutex.withLock {
            _state.update { it.copy(status = SyncStatus.SYNCING, message = null) }
            try {
                for (t in tables) pull(u, t)
                pushLocked(u)
                _state.update { it.copy(status = SyncStatus.IDLE, lastSyncAt = nowMillis(), pending = pendingCount()) }
                true
            } catch (e: Exception) {
                _state.update { it.copy(status = SyncStatus.ERROR, message = e.message, pending = pendingCount()) }
                false
            }
        }
    }

    suspend fun pushPending() {
        val u = uid ?: return
        if (!isOnline()) {
            _state.update { it.copy(status = SyncStatus.OFFLINE, pending = pendingCount()) }
            return
        }
        mutex.withLock {
            try {
                pushLocked(u)
                _state.update { it.copy(status = SyncStatus.IDLE, lastSyncAt = nowMillis(), pending = pendingCount()) }
            } catch (e: Exception) {
                _state.update { it.copy(status = SyncStatus.ERROR, message = e.message, pending = pendingCount()) }
            }
        }
    }

    private suspend fun pushLocked(u: String) {
        var sawStale = false
        for (t in tables) {
            for (doc in t.pendingDocs()) {
                val data = doc.data + ("uid" to u)
                when (remote.push(u, t.collection, doc.id, data)) {
                    PushOutcome.OK -> t.markClean(doc.id, doc.opId)
                    PushOutcome.STALE_REJECTED -> sawStale = true
                    PushOutcome.FAILED -> Unit
                }
            }
        }
        // رفض "أقدم من البعيد" يعني أن النسخة البعيدة أحدث: نسحبها فتحل محل المحلية.
        if (sawStale) for (t in tables) pull(u, t)
    }

    private suspend fun pull(u: String, t: AnySyncTable) {
        val since = checkpoints.get(u, t.collection)
        val docs = remote.fetchSince(u, t.collection, since)
        applyAndAdvance(u, t, docs, since)
    }

    private suspend fun applyAndAdvance(u: String, t: AnySyncTable, docs: List<RemoteDoc>, since: Long) {
        if (docs.isEmpty()) return
        val res = t.applyRemote(docs)
        if (res.maxUpdatedAt > 0) hybridClock.observe(res.maxUpdatedAt)
        if (res.maxUpdatedAt > since) checkpoints.set(u, t.collection, res.maxUpdatedAt)
    }

    private suspend fun startListening() {
        val u = uid ?: return
        for (t in tables) {
            val since = checkpoints.get(u, t.collection)
            listeners += remote.listen(
                u, t.collection, since,
                onDocs = { docs ->
                    scope.launch {
                        mutex.withLock { applyAndAdvance(u, t, docs, checkpoints.get(u, t.collection)) }
                    }
                },
                onError = { e -> _state.update { it.copy(status = SyncStatus.ERROR, message = e.message) } },
            )
        }
    }

    private fun stopListening() {
        listeners.forEach { runCatching { it.close() } }
        listeners.clear()
    }

    private suspend fun pendingCount(): Int = tables.sumOf { it.dirtyCount() }

    /** تنظيف شواهد الحذف القديمة التي وصلت للسحابة (تُستدعى بشكل دوري). */
    suspend fun purgeOldTombstones(olderThanMillis: Long) {
        val cutoff = nowMillis() - olderThanMillis
        tables.forEach { it.purge(cutoff) }
    }

    fun stop() {
        stopListening()
        debounceJob?.cancel()
    }
}
