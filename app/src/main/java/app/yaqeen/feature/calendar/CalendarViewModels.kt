package app.yaqeen.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.yaqeen.AppContainer
import app.yaqeen.core.HijriCalendar
import app.yaqeen.core.newId
import app.yaqeen.data.local.CalendarEventEntity
import app.yaqeen.data.settings.Settings
import app.yaqeen.data.settings.StringKey
import app.yaqeen.domain.model.SettingScope
import app.yaqeen.feature.tasks.AdhkarCategoryInfo
import app.yaqeen.feature.tasks.ArabicFormat
import app.yaqeen.feature.tasks.DaySummary
import app.yaqeen.feature.tasks.DayEndSummary
import app.yaqeen.feature.tasks.TodayTask
import app.yaqeen.feature.tasks.WorshipDay
import app.yaqeen.feature.tasks.observeWorshipDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** إعدادات خاصة بالتقويم (تُزامَن مع إعدادات المستخدم). */
object CalendarSettings {
    /** HIJRI | GREGORIAN: التقويم الأساسي في الشبكة الشهرية. */
    val PRIMARY = StringKey(SettingScope.USER, "calendar_primary", CalendarMode.HIJRI.name)
}

/** كتابة أحداث التقويم الشخصية (عبر writer، وتطلب إعادة جدولة التذكيرات). */
class CalendarRepository(private val c: AppContainer) {
    suspend fun load(id: String): CalendarEventEntity? = c.db.calendarEvents().byId(id)?.takeIf { !it.deleted }

    suspend fun save(existing: CalendarEventEntity?, title: String, notes: String, date: LocalDate, time: String?, reminder: Boolean): CalendarEventEntity {
        val t = ArabicFormat.parseTime(time)?.let { ArabicFormat.formatHHmm(it.hour, it.minute) }
        val base = existing ?: CalendarEventEntity(id = newId(), title = "", date = date.toString(), createdAt = c.clock.nowMillis())
        val row = base.copy(
            title = title.trim(), notes = notes.trim(), date = date.toString(), timeOfDay = t,
            reminderEnabled = reminder, deleted = false,
        )
        val saved = c.writer.save(c.db.calendarEvents(), row)
        c.requestReschedule()
        return saved
    }

    suspend fun delete(e: CalendarEventEntity) {
        c.writer.delete(c.db.calendarEvents(), e)
        c.requestReschedule()
    }
}

// =====================================================================================
// الشبكة الشهرية
// =====================================================================================

data class MonthUi(
    val grid: MonthGrid,
    val indicators: Map<String, DayIndicator>,
    /** تواريخ (ISO) فيها أحداث شخصية. */
    val personalDates: Set<String>,
    /** مناسبات إسلامية تبدأ في اليوم (للعلامات) مفهرسة بالتاريخ. */
    val islamicByDate: Map<LocalDate, List<IslamicEvent>>,
    val stats: MonthStats,
    val today: LocalDate,
    val adjust: Int,
)

sealed interface CalendarUi {
    data object Loading : CalendarUi
    data object Failed : CalendarUi
    data class Ready(val month: MonthUi) : CalendarUi
}

class CalendarVm(private val c: AppContainer) : ViewModel() {
    val mode: StateFlow<CalendarMode> = c.settings.observe(CalendarSettings.PRIMARY)
        .map { runCatching { CalendarMode.valueOf(it) }.getOrDefault(CalendarMode.HIJRI) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarMode.HIJRI)

    val adjust: StateFlow<Int> = c.settings.observe(Settings.HIJRI_ADJUST_DAYS).map { it.coerceIn(-2, 2) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** أي يوم داخل الشهر المعروض. */
    private val focus = MutableStateFlow(c.clock.today())

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<CalendarUi> = combine(mode, adjust, focus) { m, a, f -> Triple(m, a, f) }
        .flatMapLatest { (m, a, f) -> monthFlow(m, a, f).map<MonthUi, CalendarUi> { CalendarUi.Ready(it) }.catch { emit(CalendarUi.Failed) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarUi.Loading)

    private fun monthFlow(mode: CalendarMode, adjust: Int, focusDate: LocalDate): Flow<MonthUi> = flow {
        val key = CalendarMath.clamp(CalendarMath.monthOf(focusDate, mode, adjust), mode)
        val grid = CalendarMath.buildMonth(mode, key, adjust)
        val from = grid.firstDay.toString()
        val to = grid.lastDay.toString()
        val db = c.db
        val g1 = combine(db.prayerLogs().observeRange(from, to), db.rawatibLogs().observeRange(from, to), db.adhkarProgress().observeRange(from, to)) { p, r, a -> Triple(p, r, a) }
        val g2 = combine(db.quranPlanLogs().observeRange(from, to), db.quranDayLogs().observeRange(from, to), db.taskCompletions().observeRange(from, to)) { l, d, t -> Triple(l, d, t) }
        val g3 = combine(db.dailySummaries().observeRange(from, to), db.calendarEvents().observeRange(from, to)) { s, e -> s to e }
        val islamic = IslamicEvents.startingBetween(grid.firstDay, grid.lastDay, adjust).groupBy { it.date }
        emitAll(
            combine(g1, g2, g3) { a, b, cc ->
                val ind = CalendarStats.indicators(a.first, a.second, a.third, b.first, b.second, b.third, cc.first)
                MonthUi(
                    grid = grid,
                    indicators = ind,
                    personalDates = cc.second.map { it.date }.toSet(),
                    islamicByDate = islamic,
                    stats = CalendarStats.monthStats(grid.days, ind, c.clock.today()),
                    today = c.clock.today(),
                    adjust = adjust,
                )
            },
        )
    }

    fun shiftMonth(delta: Int) {
        val m = mode.value
        val a = adjust.value
        val key = CalendarMath.shift(CalendarMath.monthOf(focus.value, m, a), m, delta)
        focus.value = CalendarMath.buildMonth(m, key, a).firstDay
    }

    fun goTo(key: MonthKey) {
        val m = mode.value
        focus.value = CalendarMath.buildMonth(m, CalendarMath.clamp(key, m), adjust.value).firstDay
    }

    fun goToday() { focus.value = c.clock.today() }

    fun setMode(m: CalendarMode) {
        viewModelScope.launch { c.settings.set(CalendarSettings.PRIMARY, m.name) }
    }

    fun setAdjust(v: Int) {
        viewModelScope.launch {
            c.settings.set(Settings.HIJRI_ADJUST_DAYS, v.coerceIn(-2, 2))
            c.requestReschedule()
        }
    }

    fun currentKey(): MonthKey = CalendarMath.monthOf(focus.value, mode.value, adjust.value)
}


// =====================================================================================
// تفاصيل يوم
// =====================================================================================

data class AdhkarRow(val categoryId: String, val title: String, val done: Int, val total: Int)

data class DayDetail(
    val date: LocalDate,
    val label: String,
    val gregorian: String,
    val islamic: List<IslamicEvent>,
    val personal: List<CalendarEventEntity>,
    val tasks: List<TodayTask>,
    val worship: WorshipDay,
    val summary: DaySummary,
    val adhkar: List<AdhkarRow>,
    val isToday: Boolean,
    val adjust: Int,
)

sealed interface DayUi {
    data object Loading : DayUi
    data object Failed : DayUi
    data class Ready(val d: DayDetail) : DayUi
}

class DayDetailVm(private val c: AppContainer) : ViewModel() {
    private val date = MutableStateFlow<LocalDate?>(null)

    fun setDate(d: LocalDate) { date.value = d }
    fun shift(days: Long) { date.value = date.value?.plusDays(days) }
    fun today(): LocalDate = c.clock.today()

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<DayUi> = date.filterNotNull().flatMapLatest { d ->
        val a = combine(c.taskEngine.observeDay(d), observeWorshipDay(c, d)) { t, w -> t to w }
        val b = combine(
            c.db.calendarEvents().observeRange(d.toString(), d.toString()),
            flow { emit(runCatching { c.adhkar() }.getOrNull()) },
            c.settings.observe(Settings.HIJRI_ADJUST_DAYS),
        ) { e, cat, adj -> Triple(e, cat, adj.coerceIn(-2, 2)) }
        combine(a, b) { x, y ->
            val (tasks, worship) = x
            val (events, catalog, adj) = y
            val infos = catalog?.categories.orEmpty()
                .filter { it.id in DayEndSummary.DAILY_ADHKAR }
                .map { AdhkarCategoryInfo(it.id, it.title, it.items.map { i -> i.id }) }
            val completed = worship.adhkar.filter { !it.deleted && it.completed }.map { it.itemId }.toSet()
            val rows = infos.map { cat -> AdhkarRow(cat.id, cat.title, cat.itemIds.count { it in completed }, cat.itemIds.size) }
            DayUi.Ready(
                DayDetail(
                    date = d,
                    label = CalendarMath.fullLabel(d, adj),
                    gregorian = HijriCalendar.gregorianLabel(d),
                    islamic = IslamicEvents.on(d, adj),
                    personal = events.sortedBy { it.timeOfDay ?: "99:99" },
                    tasks = tasks,
                    worship = worship,
                    summary = DayEndSummary.build(d, tasks, worship, infos),
                    adhkar = rows,
                    isToday = d == c.clock.today(),
                    adjust = adj,
                ),
            ) as DayUi
        }.catch { emit(DayUi.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DayUi.Loading)
}

// =====================================================================================
// محرر الحدث الشخصي
// =====================================================================================

data class EventDraft(
    val id: String? = null,
    val title: String = "",
    val notes: String = "",
    val date: LocalDate,
    val time: String? = null,
    val reminder: Boolean = false,
)

fun validateEvent(d: EventDraft): String? = when {
    d.title.isBlank() -> "اكتب عنوان الحدث"
    else -> null
}

sealed interface EventEditorUi {
    data object Loading : EventEditorUi
    data object NotFound : EventEditorUi
    data class Editing(val draft: EventDraft, val isNew: Boolean, val error: String? = null, val saving: Boolean = false) : EventEditorUi
}

class EventEditorVm(private val c: AppContainer) : ViewModel() {
    private val repo = CalendarRepository(c)
    private val _state = MutableStateFlow<EventEditorUi>(EventEditorUi.Loading)
    val state: StateFlow<EventEditorUi> = _state.asStateFlow()
    private var existing: CalendarEventEntity? = null
    private var initKey: String? = null

    fun init(id: String, date: LocalDate) {
        val key = "$id|$date"
        if (initKey == key) return
        initKey = key
        viewModelScope.launch {
            if (id == "new") {
                _state.value = EventEditorUi.Editing(EventDraft(date = date), isNew = true)
            } else {
                val e = repo.load(id)
                existing = e
                _state.value = if (e == null) EventEditorUi.NotFound else EventEditorUi.Editing(
                    EventDraft(e.id, e.title, e.notes, ArabicFormat.parseDate(e.date) ?: date, e.timeOfDay, e.reminderEnabled),
                    isNew = false,
                )
            }
        }
    }

    fun update(f: (EventDraft) -> EventDraft) {
        val s = _state.value as? EventEditorUi.Editing ?: return
        _state.value = s.copy(draft = f(s.draft), error = null)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value as? EventEditorUi.Editing ?: return
        if (s.saving) return
        val err = validateEvent(s.draft)
        if (err != null) { _state.value = s.copy(error = err); return }
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            val d = s.draft
            runCatching { repo.save(existing, d.title, d.notes, d.date, d.time, d.reminder) }
                .onSuccess { onDone() }
                .onFailure { _state.value = s.copy(saving = false, error = "تعذّر الحفظ. حاول مرة أخرى.") }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            existing?.let { repo.delete(it) }
            onDone()
        }
    }
}

// =====================================================================================
// قائمة المناسبات
// =====================================================================================

data class EventsListData(
    val islamic: List<IslamicEvent>,
    val personal: List<CalendarEventEntity>,
    val adjust: Int,
    val today: LocalDate,
)

class EventsListVm(private val c: AppContainer) : ViewModel() {
    val data: StateFlow<EventsListData?> = combine(
        c.settings.observe(Settings.HIJRI_ADJUST_DAYS),
        c.db.calendarEvents().observeAll(),
    ) { adj, personal ->
        val a = adj.coerceIn(-2, 2)
        val today = c.clock.today()
        EventsListData(
            islamic = IslamicEvents.upcoming(today, a, 60),
            personal = personal.filter { e -> ArabicFormat.parseDate(e.date)?.let { !it.isBefore(today) } ?: false },
            adjust = a,
            today = today,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
