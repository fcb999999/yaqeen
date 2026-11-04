package app.yaqeen.feature.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.data.quran.QuranData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** كل ما تعرضه شاشة "رحلتي" لفترة محددة. */
data class JourneyData(
    val today: LocalDate,
    val period: PeriodRange,
    val hasAnyData: Boolean,
    val streak: Streak,
    val activeDaysInPeriod: Int,
    val heatmap: HeatmapGrid,
    val weekBars: List<DayPoint>,
    val monthSeries: List<DayPoint>,
    val quran: QuranStats,
    val wird: WirdCommitment,
    val plans: PlansOverview,
    val adhkar: AdhkarStats,
    val prayers: PrayerStatsSummary,
    val rawatib: RawatibStats,
    val tasks: TasksStats,
    val comparison: PeriodComparison,
    val encouragement: String,
)

sealed interface JourneyUiState {
    data object Loading : JourneyUiState
    data class Ready(val data: JourneyData, val updateFailed: Boolean) : JourneyUiState
}

/** يجمّع حسابات الشاشة من الملخصات المخزّنة (Flow) دون إعادة حساب السجلات الخام عند كل إطار. */
class JourneyViewModel(private val c: AppContainer) : ViewModel() {
    private val updater = JourneySummaryUpdater(c)

    private val _period = MutableStateFlow(30)
    val period: StateFlow<Int> = _period

    private val firstPassDone = MutableStateFlow(false)
    private val updateFailed = MutableStateFlow(false)
    private val quran = MutableStateFlow<QuranData?>(null)

    init {
        viewModelScope.launch { quran.value = runCatching { c.quran() }.getOrNull() }
        viewModelScope.launch(Dispatchers.Default) {
            updater.runForever { error ->
                updateFailed.value = error != null
                firstPassDone.value = true
            }
        }
    }

    fun setPeriod(days: Int) {
        if (days in JourneyConfig.PERIODS) _period.value = days
    }

    /** إعادة محاولة حساب الملخصات بعد فشل. */
    fun retry() {
        viewModelScope.launch(Dispatchers.Default) {
            val failure = runCatching { updater.refreshOnce() }.exceptionOrNull()
            updateFailed.value = failure != null
            firstPassDone.value = true
        }
    }

    private data class Raw(
        val today: LocalDate,
        val summaries: List<app.yaqeen.data.local.DailySummaryEntity>,
        val planLogs: List<app.yaqeen.data.local.QuranPlanLogEntity>,
        val activePlanIds: Set<String>,
        val plans: List<app.yaqeen.data.local.QuranPlanEntity>,
    )

    @OptIn(ExperimentalCoroutinesApi::class)
    private val raw = DayTicker.today(c.clock).flatMapLatest { today ->
        val from = today.minusDays((JourneyConfig.SUMMARY_DAYS - 1).toLong()).toString()
        val to = today.toString()
        combine(
            c.db.dailySummaries().observeRange(from, to),
            c.db.quranPlanLogs().observeRange(from, to),
            c.db.quranPlans().observeAll(),
        ) { summaries, logs, plans ->
            Raw(today, summaries, logs, plans.filter { it.status == "ACTIVE" }.map { it.id }.toSet(), plans)
        }
    }

    val state: StateFlow<JourneyUiState> = combine(raw, _period, quran, firstPassDone, updateFailed) { r, days, q, done, failed ->
        if (!done && r.summaries.isEmpty()) JourneyUiState.Loading
        else JourneyUiState.Ready(build(r, days, q), failed)
    }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), JourneyUiState.Loading)

    private fun build(r: Raw, days: Int, q: QuranData?): JourneyData {
        val period = PeriodRange.last(r.today, days)
        val s = r.summaries
        val activeDays = StreakCalculator.activeDays(s)
        val streak = StreakCalculator.compute(activeDays, r.today)
        val periodActive = activeDays.count { it in period }
        val hasAny = s.any { it.isMeaningful() }
        val pageOf: ((Int) -> Int)? = q?.let { qd -> { id: Int -> qd.ayah(id.coerceIn(1, qd.totalAyahs)).page } }
        return JourneyData(
            today = r.today,
            period = period,
            hasAnyData = hasAny,
            streak = streak,
            activeDaysInPeriod = periodActive,
            heatmap = HeatmapBuilder.build(s, r.today),
            weekBars = ChartBuilder.daily(s, r.today, 7),
            monthSeries = ChartBuilder.daily(s, r.today, 30),
            quran = PeriodAggregator.quran(s, period, q?.pageCount ?: 604),
            wird = PeriodAggregator.wird(r.planLogs, r.activePlanIds, period),
            plans = PeriodAggregator.plans(r.plans, pageOf),
            adhkar = PeriodAggregator.adhkar(s, period),
            prayers = PeriodAggregator.prayers(s, period, PeriodAggregator.trackingStart(s), r.today),
            rawatib = PeriodAggregator.rawatib(s, period),
            tasks = PeriodAggregator.tasks(s, period),
            comparison = PeriodComparer.compare(s, period),
            encouragement = Encouragement.forJourney(periodActive, days, streak.current, hasAny, r.today.toEpochDay()),
        )
    }
}
