package app.yaqeen.feature.prayer.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.core.HijriCalendar
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZonedDateTime

/** حالة صفحة الصلاة. [times] = null إذا لم تتوفر المواقيت (لا موقع أو لم تُنفَّذ الخدمة بعد). */
data class PrayerHomeUi(
    val loading: Boolean = true,
    val error: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val hijri: String = "",
    val statuses: Map<Prayer, PrayerStatus> = emptyMap(),
    val checklist: RawatibDay = RawatibDay(emptyList()),
    val times: Map<Prayer, ZonedDateTime>? = null,
    /** موضع الوقت الحالي؛ يُحسب لليوم الحالي فقط. */
    val timeline: PrayerTimeline? = null,
) {
    val isToday: Boolean get() = date == today
    val canGoNext: Boolean get() = date.isBefore(today)
    val canEdit: Boolean get() = !date.isAfter(today)
}

private data class TimesBundle(val day: Map<Prayer, ZonedDateTime>, val tomorrowFajr: ZonedDateTime?)

@OptIn(ExperimentalCoroutinesApi::class)
class PrayerHomeViewModel(private val c: AppContainer) : ViewModel() {
    val repo = PrayerTrackingRepository(c)

    private val selected = MutableStateFlow<LocalDate?>(null)
    private val retryTick = MutableStateFlow(0)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private fun nowZoned(): ZonedDateTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(c.clock.nowMillis()), c.clock.zone())

    /** ساعة تدق عند بداية كل دقيقة (للوقت المتبقي وتغيّر اليوم عند منتصف الليل). */
    private val nowFlow: StateFlow<ZonedDateTime> = flow {
        while (true) {
            val ms = c.clock.nowMillis()
            emit(nowZoned())
            delay(60_000L - (ms % 60_000L) + 100L)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), nowZoned())

    private suspend fun <T> safe(block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        null
    }

    private val locationFlow: Flow<Any?> = flow { emitAll(c.prayerTimes.observeLocation()) }.catch { emit(null) }

    /** المواقيت من خدمة المواقيت (قد تكون null بلا موقع). تتجدد عند تغيّر الموقع. */
    private fun timesFlow(date: LocalDate, today: LocalDate): Flow<TimesBundle?> =
        locationFlow.mapLatest {
            val day = safe { c.prayerTimes.timesFor(date) } ?: return@mapLatest null
            val tomorrowFajr = if (date == today) safe { c.prayerTimes.timesFor(today.plusDays(1)) }?.fajr else null
            TimesBundle(Prayer.entries.associateWith { day.timeOf(it) }, tomorrowFajr)
        }

    val ui: StateFlow<PrayerHomeUi> = combine(selected, nowFlow, retryTick) { sel, now, _ ->
        val today = now.toLocalDate()
        (if (sel == null || sel.isAfter(today)) today else sel) to today
    }
        .distinctUntilChanged()
        .flatMapLatest { (date, today) ->
            combine(
                repo.observeDay(date),
                repo.observeChecklist(date),
                timesFlow(date, today),
                c.settings.observe(Settings.HIJRI_ADJUST_DAYS),
                nowFlow,
            ) { statuses, checklist, times, adjust, now ->
                PrayerHomeUi(
                    loading = false,
                    date = date,
                    today = today,
                    hijri = HijriCalendar.fromGregorian(date, adjust).format(),
                    statuses = statuses,
                    checklist = checklist,
                    times = times?.day,
                    timeline = if (date == today && times != null) computeTimeline(times.day, times.tomorrowFajr, now) else null,
                )
            }
        }
        .catch { emit(PrayerHomeUi(loading = false, error = true)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrayerHomeUi())

    fun retry() { retryTick.value++ }

    fun select(date: LocalDate) { selected.value = date }

    fun previousDay() { selected.value = ui.value.date.minusDays(1) }

    fun nextDay() {
        val s = ui.value
        if (s.canGoNext) selected.value = s.date.plusDays(1)
    }

    fun goToday() { selected.value = null }

    fun toggle(prayer: Prayer, status: PrayerStatus) = launchWrite { repo.toggleStatus(ui.value.date, prayer, status) }

    fun setSunnah(item: SunnahItem, done: Boolean) = launchWrite { repo.setSunnahDone(ui.value.date, item, done) }

    private fun launchWrite(block: suspend () -> Boolean) {
        // نطاق التطبيق حتى لا يضيع تسجيل نُقر قبل مغادرة الشاشة مباشرة.
        c.appScope.launch {
            val ok = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                _messages.tryEmit("تعذّر حفظ التسجيل، حاول مرة أخرى")
                return@launch
            }
            if (!ok) _messages.tryEmit("لا يمكن التسجيل ليوم لم يأتِ بعد")
        }
    }
}
