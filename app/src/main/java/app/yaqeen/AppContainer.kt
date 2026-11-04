package app.yaqeen

import android.content.Context
import app.yaqeen.core.Clock
import app.yaqeen.core.HybridClock
import app.yaqeen.core.SystemClockImpl
import app.yaqeen.data.adhkar.AdhkarCatalog
import app.yaqeen.data.adhkar.AdhkarCatalogLoader
import app.yaqeen.data.auth.AuthRepository
import app.yaqeen.data.auth.AuthState
import app.yaqeen.data.local.AdhkarMarkEntity
import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.BookmarkCategoryEntity
import app.yaqeen.data.local.BookmarkEntity
import app.yaqeen.data.local.CalendarEventEntity
import app.yaqeen.data.local.CustomSunnahEntity
import app.yaqeen.data.local.DailySummaryEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranDayLogEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.data.local.ReadingProgressEntity
import app.yaqeen.data.local.SettingEntity
import app.yaqeen.data.local.TaskCompletionEntity
import app.yaqeen.data.local.TaskEntity
import app.yaqeen.data.local.YaqeenDatabase
import app.yaqeen.data.prefs.LocalPrefs
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.QuranDataLoader
import app.yaqeen.data.settings.SettingsRepository
import app.yaqeen.data.sync.AnySyncTable
import app.yaqeen.data.sync.Connectivity
import app.yaqeen.data.sync.DeviceInfo
import app.yaqeen.data.sync.FirestoreRemoteStore
import app.yaqeen.data.sync.RemoteDoc
import app.yaqeen.data.sync.RemoteStore
import app.yaqeen.data.sync.PushOutcome
import app.yaqeen.data.sync.RoomSyncTable
import app.yaqeen.data.sync.Stamper
import app.yaqeen.data.sync.SyncManager
import app.yaqeen.data.sync.SyncWriter
import app.yaqeen.notifications.Channels
import app.yaqeen.notifications.ReminderScheduler
import app.yaqeen.notifications.RescheduleCoordinator
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Closeable

/**
 * حاوية الاعتماديات (حقن يدوي بدل Hilt لتقليل تعقيد البناء). كل شيء هنا مفرد (singleton) طوال عمر التطبيق.
 *
 * قواعد الاستخدام:
 *  - الكتابة في بيانات المستخدم تمر دائمًا عبر [writer] (يختم ويزامن)، لا عبر dao.upsert مباشرة.
 *  - القراءة تتم بـ Flow من الـDAOs في [db].
 *  - بيانات القرآن والأذكار ثابتة وتُحمَّل من الأصول عبر [quran] و[adhkar].
 */
class AppContainer(val context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = SystemClockImpl

    val device = DeviceInfo(context)
    private val hybridClock = HybridClock(clock)
    val stamper = Stamper(device.deviceId, hybridClock)
    val writer = SyncWriter(stamper)

    val db: YaqeenDatabase by lazy { YaqeenDatabase.create(context) }
    val localPrefs = LocalPrefs(context)
    val settings: SettingsRepository by lazy { SettingsRepository(db.settings(), writer) }
    val auth = AuthRepository(context)
    val connectivity = Connectivity(context)
    val reminders = ReminderScheduler(context)

    private val syncTables: List<AnySyncTable> by lazy {
        listOf(
            RoomSyncTable("reading_progress", ReadingProgressEntity.serializer(), db.readingProgress()),
            RoomSyncTable("quran_bookmark_categories", BookmarkCategoryEntity.serializer(), db.bookmarkCategories()),
            RoomSyncTable("quran_bookmarks", BookmarkEntity.serializer(), db.bookmarks()),
            RoomSyncTable("quran_plans", QuranPlanEntity.serializer(), db.quranPlans()),
            RoomSyncTable("quran_plan_logs", QuranPlanLogEntity.serializer(), db.quranPlanLogs()),
            RoomSyncTable("quran_day_logs", QuranDayLogEntity.serializer(), db.quranDayLogs()),
            RoomSyncTable("adhkar_progress", AdhkarProgressEntity.serializer(), db.adhkarProgress()),
            RoomSyncTable("adhkar_favorites", AdhkarMarkEntity.serializer(), db.adhkarMarks()),
            RoomSyncTable("prayers_tracking", PrayerLogEntity.serializer(), db.prayerLogs()),
            RoomSyncTable("rawatib_tracking", RawatibLogEntity.serializer(), db.rawatibLogs()),
            RoomSyncTable("custom_sunnah", CustomSunnahEntity.serializer(), db.customSunnah()),
            RoomSyncTable("daily_tasks", TaskEntity.serializer(), db.tasks()),
            RoomSyncTable("task_completions", TaskCompletionEntity.serializer(), db.taskCompletions()),
            RoomSyncTable("calendar_events", CalendarEventEntity.serializer(), db.calendarEvents()),
            // الإعدادات تُقسَّم إلى مجموعتين حسب النطاق
            ScopedSettingsTable("user_settings", app.yaqeen.domain.model.SettingScope.USER),
            ScopedSettingsTable("notification_settings", app.yaqeen.domain.model.SettingScope.NOTIFICATION),
            RoomSyncTable("achievement_statistics", DailySummaryEntity.serializer(), db.dailySummaries()),
        )
    }

    /** الإعدادات: جدول Room واحد يُزامَن إلى مجموعتي Firestore (user_settings, notification_settings) حسب scope. */
    private inner class ScopedSettingsTable(
        override val collection: String,
        private val scope: String,
    ) : AnySyncTable {
        private val inner = RoomSyncTable("settings", SettingEntity.serializer(), db.settings())
        override suspend fun pendingDocs() = inner.pendingDocs().filter { it.data["scope"] == scope }
        override suspend fun markClean(id: String, opId: String) = inner.markClean(id, opId)
        override suspend fun applyRemote(docs: List<RemoteDoc>) = inner.applyRemote(docs.filter { it.data["scope"] == scope })
        override suspend fun purge(before: Long) = inner.purge(before)
        override suspend fun dirtyCount(): Int = pendingDocs().size
    }

    private val remoteStore: RemoteStore by lazy {
        if (auth.isAvailable) FirestoreRemoteStore(FirebaseFirestore.getInstance()) else NoopRemoteStore
    }

    val sync: SyncManager by lazy {
        SyncManager(
            tables = syncTables,
            remote = remoteStore,
            checkpoints = localPrefs,
            hybridClock = hybridClock,
            isOnline = connectivity::isOnline,
            scope = appScope,
        )
    }

    // ---- خدمات الميزات المشتركة (عقود بين الميزات؛ ينفّذها أصحابها) ----
    val prayerTimes: app.yaqeen.feature.prayer.times.PrayerTimesService by lazy { app.yaqeen.feature.prayer.times.PrayerTimesService(this) }
    val taskEngine: app.yaqeen.feature.tasks.TaskEngine by lazy { app.yaqeen.feature.tasks.TaskEngine(this) }
    val readingTracker: app.yaqeen.feature.quran.plans.ReadingTracker by lazy { app.yaqeen.feature.quran.plans.ReadingTracker(this) }
    val quranAudio: app.yaqeen.feature.quran.audio.QuranAudioController by lazy { app.yaqeen.feature.quran.audio.QuranAudioController(this) }

    suspend fun quran(): QuranData = QuranDataLoader.load(context)
    suspend fun adhkar(): AdhkarCatalog = AdhkarCatalogLoader.load(context)

    private var rescheduleJob: Job? = null

    /** يطلب إعادة جدولة كل التذكيرات (مؤجلة ومجمّعة). يُستدعى بعد أي تغيير يؤثر على الأوقات أو الإعدادات. */
    fun requestReschedule() {
        rescheduleJob?.cancel()
        rescheduleJob = appScope.launch {
            delay(500)
            RescheduleCoordinator.run(this@AppContainer)
        }
    }

    /** يُستدعى مرة عند إقلاع التطبيق. */
    fun start() {
        Channels.createAll(context)
        writer.setPushTrigger { sync.requestPush() }
        appScope.launch {
            auth.state.collect { st ->
                when (st) {
                    is AuthState.SignedIn -> {
                        handleAccountSwitch(st.uid)
                        sync.setUser(st.uid)
                    }
                    else -> sync.setUser(null)
                }
            }
        }
        requestReschedule()
    }

    /**
     * إذا سجّل مستخدم مختلف دخوله على الجهاز نفسه تُستبدل بيانات الجهاز ببيانات حسابه الجديد
     * (حتى لا تختلط بيانات حسابين). بيانات الوضع المحلي (قبل أي تسجيل) تُضم إلى أول حساب يسجَّل به.
     */
    private suspend fun handleAccountSwitch(uid: String) {
        val prev = localPrefs.get(LocalPrefs.Keys.SYNCED_UID)
        if (prev != null && prev != uid) {
            withContext(Dispatchers.IO) { db.clearAllTables() }
            localPrefs.clearCheckpoints()
        }
        localPrefs.put(LocalPrefs.Keys.SYNCED_UID, uid)
    }
}

/** بديل بلا شبكة عند عدم إعداد Firebase: لا يُرسل ولا يستقبل شيئًا. */
private object NoopRemoteStore : RemoteStore {
    override suspend fun push(uid: String, collection: String, docId: String, data: Map<String, Any?>) = PushOutcome.FAILED
    override suspend fun fetchSince(uid: String, collection: String, sinceUpdatedAt: Long) = emptyList<RemoteDoc>()
    override fun listen(uid: String, collection: String, sinceUpdatedAt: Long, onDocs: (List<RemoteDoc>) -> Unit, onError: (Throwable) -> Unit) = Closeable { }
    override suspend fun writeProfile(uid: String, profile: Map<String, Any?>) = Unit
}
