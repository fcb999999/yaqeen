package app.yaqeen.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.core.HijriCalendar
import app.yaqeen.core.HijriDate
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.ReadingProgressEntity
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.calendar.IslamicEvent
import app.yaqeen.feature.calendar.IslamicEvents
import app.yaqeen.feature.journey.AdhkarItemIndex
import app.yaqeen.feature.journey.DayTicker
import app.yaqeen.feature.prayer.times.NextPrayer
import app.yaqeen.feature.tasks.DayProgress
import app.yaqeen.feature.tasks.TodayTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/** كل ما تحتاجه الرئيسية لرسم يوم واحد. */
data class HomeData(
    val date: LocalDate,
    val hijri: HijriDate,
    val prayers: List<PrayerChip>,
    val rawatib: RawatibToday,
    /** null حتى يُحمَّل كتالوج الأذكار. */
    val adhkar: List<AdhkarStatusRow>?,
    val wird: List<WirdTodayItem>,
    val reading: ReadingTarget?,
    val tasks: List<TodayTask>,
    val taskProgress: DayProgress,
    val event: IslamicEvent?,
    val overall: OverallProgress,
    val sectionsCsv: String,
)

/** موضع "أكمل القراءة". */
data class ReadingTarget(val surah: Int, val ayah: Int, val page: Int, val surahName: String?)

sealed interface NextPrayerState {
    data object Loading : NextPrayerState
    /** لا موقع محدد (أو لا مواقيت). */
    data object NoLocation : NextPrayerState
    data class Ready(val next: NextPrayer) : NextPrayerState
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data class Ready(val data: HomeData) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {

    private val retry = MutableStateFlow(0)
    private val adhkarIndex = MutableStateFlow<AdhkarItemIndex?>(null)
    private val quran = MutableStateFlow<QuranData?>(null)

    private val _nextPrayer = MutableStateFlow<NextPrayerState>(NextPrayerState.Loading)
    val nextPrayer: StateFlow<NextPrayerState> = _nextPrayer

    init {
        viewModelScope.launch {
            runCatching { c.adhkar() }.getOrNull()?.let { cat ->
                adhkarIndex.value = AdhkarItemIndex(cat.categories.associate { k -> k.id to k.items.map { it.id }.toSet() })
            }
        }
        viewModelScope.launch { quran.value = runCatching { c.quran() }.getOrNull() }
        viewModelScope.launch { trackNextPrayer() }
    }

    // ---------------------------------------------------------- الصلاة القادمة

    /**
     * يحدّث الصلاة القادمة عند تغيّر الموقع، وبعد انقضاء وقت الصلاة الحالية، ودوريًا (كل 30 ثانية) إن لم يوجد موقع
     * (ليظهر الوقت فور اختيار الموقع حتى لو لم يصل بث الموقع).
     */
    private suspend fun trackNextPrayer() {
        val version = MutableStateFlow(0)
        coroutineScope {
            launch { runCatching { c.prayerTimes.observeLocation().collect { version.value = version.value + 1 } } }
            version.collectLatest {
                while (true) {
                    val next = runCatching { c.prayerTimes.nextPrayer(ZonedDateTime.now()) }.getOrNull()
                    if (next == null) {
                        _nextPrayer.value = NextPrayerState.NoLocation
                        delay(30_000)
                    } else {
                        _nextPrayer.value = NextPrayerState.Ready(next)
                        val wait = java.time.Duration.between(ZonedDateTime.now(), next.time).toMillis() + 1_000
                        delay(wait.coerceIn(1_000L, 15 * 60_000L))
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------- حالة اليوم

    private data class DayWorship(
        val date: LocalDate,
        val prayers: List<PrayerLogEntity>,
        val rawatib: List<app.yaqeen.data.local.RawatibLogEntity>,
        val adhkar: List<app.yaqeen.data.local.AdhkarProgressEntity>,
        val planLogs: List<app.yaqeen.data.local.QuranPlanLogEntity>,
        val plans: List<app.yaqeen.data.local.QuranPlanEntity>,
    )

    private data class TaskSlice(val tasks: List<TodayTask>, val progress: DayProgress)
    private data class Prefs(val csv: String, val adjust: Int)
    private data class Statics(val adhkar: AdhkarItemIndex?, val quran: QuranData?)

    private val dayFlow = DayTicker.today(c.clock).flatMapLatest { date ->
        val key = date.toString()
        combine(
            c.db.prayerLogs().observeDay(key),
            c.db.rawatibLogs().observeDay(key),
            c.db.adhkarProgress().observeDay(key),
            c.db.quranPlanLogs().observeRange(key, key),
            c.db.quranPlans().observeActive(),
        ) { p, r, a, l, plans -> DayWorship(date, p, r, a, l, plans) }
    }

    private val taskFlow = DayTicker.today(c.clock).flatMapLatest { date ->
        combine(
            c.taskEngine.observeDay(date).catch { emit(emptyList()) },
            c.taskEngine.observeProgress(date).catch { emit(DayProgress(0, 0)) },
        ) { t, p -> TaskSlice(t, p) }
    }

    val state: StateFlow<HomeUiState> = retry.flatMapLatest {
        combine(
            dayFlow,
            taskFlow,
            c.db.readingProgress().observeLatest(),
            combine(c.settings.observe(Settings.HOME_SECTIONS), c.settings.observe(Settings.HIJRI_ADJUST_DAYS)) { csv, adj -> Prefs(csv, adj) },
            combine(adhkarIndex, quran) { a, q -> Statics(a, q) },
        ) { day, tasks, reading, prefs, statics -> HomeUiState.Ready(build(day, tasks, reading, prefs, statics)) as HomeUiState }
            .catch { emit(HomeUiState.Error("تعذّر تحميل الرئيسية. حاول مرة أخرى.")) }
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState.Loading)

    private fun build(day: DayWorship, tasks: TaskSlice, reading: ReadingProgressEntity?, prefs: Prefs, statics: Statics): HomeData {
        val q = statics.quran
        val pageOf: ((Int) -> Int)? = q?.let { qd -> { id: Int -> qd.ayah(id.coerceIn(1, qd.totalAyahs)).page } }
        val chips = PrayersToday.chips(day.prayers)
        val rawatib = RawatibSummary.of(day.rawatib)
        val adhkar = statics.adhkar?.let { AdhkarToday.rows(it, day.adhkar) }
        val wird = WirdToday.items(day.plans, day.planLogs, day.date.toString(), q?.pageCount ?: 604, pageOf)
        val manual = tasks.tasks.filter { !it.derived }
        val overall = OverallCompletion.compute(
            prayed = PrayersToday.prayed(chips),
            rawatibDone = rawatib.doneDefaults,
            adhkarRows = adhkar.orEmpty(),
            wird = wird,
            manualTasksTotal = manual.size,
            manualTasksDone = manual.count { it.done },
        )
        val event = runCatching { IslamicEvents.upcoming(day.date, prefs.adjust, 1).firstOrNull() }.getOrNull()
        val target = reading?.takeIf { !it.deleted }?.let {
            ReadingTarget(it.surah, it.ayah, it.page, runCatching { q?.surah(it.surah)?.nameAr }.getOrNull())
        }
        return HomeData(
            date = day.date,
            hijri = HijriCalendar.fromGregorian(day.date, prefs.adjust),
            prayers = chips,
            rawatib = rawatib,
            adhkar = adhkar,
            wird = wird,
            reading = target,
            tasks = tasks.tasks,
            taskProgress = tasks.progress,
            event = event,
            overall = overall,
            sectionsCsv = prefs.csv,
        )
    }

    // ---------------------------------------------------------- الإجراءات

    fun reload() { retry.value = retry.value + 1 }

    fun saveSections(csv: String) {
        viewModelScope.launch { c.settings.set(Settings.HOME_SECTIONS, csv) }
    }
}
