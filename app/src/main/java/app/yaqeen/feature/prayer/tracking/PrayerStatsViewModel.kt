package app.yaqeen.feature.prayer.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class PrayerStatsUi(
    val loading: Boolean = true,
    val error: Boolean = false,
    val period: StatsPeriod = StatsPeriod.WEEK,
    val anchor: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val range: DateRange = DateRange(LocalDate.now(), LocalDate.now()),
    val prayer: PrayerRangeStats? = null,
    val rawatib: RawatibRangeStats? = null,
    /** أسماء السنن المخصصة حسب "custom:<id>". */
    val customNames: Map<String, String> = emptyMap(),
) {
    val canGoNext: Boolean get() = StatsRanges.canGoNext(period, anchor, today)
    val canNavigate: Boolean get() = !period.isRolling
}

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerStatsViewModel(private val c: AppContainer) : ViewModel() {
    val repo = PrayerTrackingRepository(c)

    private val period = MutableStateFlow(StatsPeriod.WEEK)
    private val anchor = MutableStateFlow<LocalDate?>(null)
    private val retryTick = MutableStateFlow(0)

    private val todayFlow = flow {
        while (true) {
            emit(c.clock.today())
            delay(60_000L)
        }
    }.distinctUntilChanged()

    val ui: StateFlow<PrayerStatsUi> = combine(period, anchor, todayFlow, retryTick) { p, a, today, _ ->
        Triple(p, (a ?: today).let { if (it.isAfter(today)) today else it }, today)
    }
        .distinctUntilChanged()
        .flatMapLatest { (p, a, today) ->
            val range = StatsRanges.range(p, a, today)
            combine(
                repo.observePrayerRange(range.from, range.to),
                repo.observeRawatibRange(range.from, range.to),
                repo.observeCustomNames(),
            ) { prayerLogs, rawatibLogs, names ->
                PrayerStatsUi(
                    loading = false,
                    period = p,
                    anchor = a,
                    today = today,
                    range = range,
                    prayer = prayerStats(prayerLogs, range.from, range.to, asOf = today),
                    rawatib = rawatibStats(rawatibLogs, range.from, range.to, asOf = today),
                    customNames = names,
                )
            }.flowOn(Dispatchers.Default)
        }
        .catch { emit(PrayerStatsUi(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrayerStatsUi())

    fun setPeriod(p: StatsPeriod) {
        // عند تغيير النوع نبدأ من اليوم ليكون العرض متوقعًا.
        if (period.value != p) anchor.value = null
        period.value = p
    }

    fun previous() {
        val s = ui.value
        if (s.canNavigate) anchor.value = StatsRanges.shift(s.period, s.anchor, -1)
    }

    fun next() {
        val s = ui.value
        if (s.canNavigate && s.canGoNext) anchor.value = StatsRanges.shift(s.period, s.anchor, 1).let { if (it.isAfter(s.today)) s.today else it }
    }

    fun retry() { retryTick.value++ }
}
