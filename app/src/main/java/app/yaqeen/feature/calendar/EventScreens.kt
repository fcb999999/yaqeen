package app.yaqeen.feature.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.core.HijriCalendar
import app.yaqeen.feature.tasks.ArabicFormat
import app.yaqeen.feature.tasks.DatePickerDialogAr
import app.yaqeen.feature.tasks.TimePickerDialogAr
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// =====================================================================================
// محرر الحدث الشخصي
// =====================================================================================

@Composable
fun EventEditorScreen(nav: NavHostController, id: String, date: LocalDate) {
    val vm = containerViewModel(key = "event-editor-$id-$date") { EventEditorVm(it) }
    LaunchedEffect(id, date) { vm.init(id, date) }
    val state by vm.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        when (val s = state) {
            EventEditorUi.Loading -> { YaqeenTopBar("حدث", onBack = { nav.popBackStack() }); LoadingBox() }
            EventEditorUi.NotFound -> {
                YaqeenTopBar("حدث", onBack = { nav.popBackStack() })
                EmptyState("لم يُعثر على الحدث", "ربما حُذف من جهاز آخر.", action = { TextButton(onClick = { nav.popBackStack() }) { Text("رجوع") } })
            }
            is EventEditorUi.Editing -> EventForm(nav, vm, s)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.EventForm(nav: NavHostController, vm: EventEditorVm, s: EventEditorUi.Editing) {
    val d = s.draft
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    YaqeenTopBar(if (s.isNew) "حدث جديد" else "تعديل الحدث", onBack = { nav.popBackStack() })
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = d.title, onValueChange = { v -> vm.update { it.copy(title = v) } },
            label = { Text("عنوان الحدث") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = d.notes, onValueChange = { v -> vm.update { it.copy(notes = v) } },
            label = { Text("ملاحظات (اختياري)") }, minLines = 2, modifier = Modifier.fillMaxWidth(),
        )
        YaqeenCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("التاريخ", style = MaterialTheme.typography.labelLarge)
                    Text(HijriCalendar.dayName(d.date) + "، " + HijriCalendar.gregorianLabel(d.date), style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedButton(onClick = { showDate = true }) { Text("تغيير") }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("الوقت: " + (ArabicFormat.time12(d.time) ?: "طوال اليوم"), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                OutlinedButton(onClick = { showTime = true }) { Text(if (d.time == null) "تحديد" else "تغيير") }
                if (d.time != null) TextButton(onClick = { vm.update { it.copy(time = null) } }) { Text("إزالة") }
            }
            SwitchRow(
                title = "تذكير بالحدث",
                subtitle = if (d.time == null) "ينبّهك في الساعة ٩:٠٠ ص من يوم الحدث" else "ينبّهك في وقت الحدث",
                checked = d.reminder,
                onCheckedChange = { on -> vm.update { it.copy(reminder = on) } },
            )
        }
        if (s.error != null) Text(s.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = { vm.save { nav.popBackStack() } }, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.isNew) "إضافة الحدث" else "حفظ التعديلات")
        }
        if (!s.isNew) {
            OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("حذف الحدث", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showDate) DatePickerDialogAr(d.date, onDismiss = { showDate = false }, onPick = { dt -> vm.update { it.copy(date = dt) } })
    if (showTime) TimePickerDialogAr(d.time, onDismiss = { showTime = false }, onPick = { t -> vm.update { it.copy(time = t) } })
    if (showDelete) {
        ConfirmDialog(
            title = "حذف الحدث", message = "سيُحذف هذا الحدث وتذكيره. هل تريد المتابعة؟", confirmText = "حذف",
            onConfirm = { vm.delete { nav.popBackStack() } }, onDismiss = { showDelete = false },
        )
    }
}

// =====================================================================================
// قائمة المناسبات القادمة
// =====================================================================================

private enum class EventsFilter(val label: String) { ALL("الكل"), MAJOR("المناسبات الكبرى"), FASTING("أيام الصيام") }

private val FASTING_KEYS = setOf(
    IslamicEventKeys.WHITE_DAYS, IslamicEventKeys.ARAFAH, IslamicEventKeys.ASHURA, IslamicEventKeys.TASUA,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventsListScreen(nav: NavHostController) {
    val vm = containerViewModel { EventsListVm(it) }
    val data by vm.data.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf(EventsFilter.ALL) }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("المناسبات", onBack = { nav.popBackStack() })
        val d = data
        if (d == null) {
            LoadingBox()
            return@Column
        }
        val islamic = when (filter) {
            EventsFilter.ALL -> d.islamic
            EventsFilter.MAJOR -> d.islamic.filter { it.notifiable && it.key != IslamicEventKeys.WHITE_DAYS }
            EventsFilter.FASTING -> d.islamic.filter { it.key in FASTING_KEYS }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (d.personal.isNotEmpty()) {
                item(key = "h_personal") { SectionTitle("أحداثي القادمة") }
                items(d.personal, key = { "p_" + it.id }) { e ->
                    val date = ArabicFormat.parseDate(e.date) ?: d.today
                    YaqeenCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(CalendarRoutes.event(e.id, date)) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(e.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    HijriCalendar.dayName(date) + " " + HijriCalendar.gregorianLabel(date) +
                                        (ArabicFormat.time12(e.timeOfDay)?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Pill(ArabicFormat.relativeDays(ChronoUnit.DAYS.between(d.today, date).toInt()))
                        }
                    }
                }
            }
            item(key = "h_islamic") {
                SectionTitle("المناسبات الإسلامية")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EventsFilter.values().forEach { f ->
                        FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                    }
                }
            }
            if (islamic.isEmpty()) {
                item(key = "empty") { EmptyState("لا مناسبات ضمن هذا التصنيف", icon = Icons.Filled.Event) }
            }
            items(islamic, key = { it.key + it.date }) { e ->
                YaqeenCard(Modifier.fillMaxWidth(), onClick = { nav.navigate(CalendarRoutes.day(e.date)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.nameAr, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${HijriCalendar.dayName(e.date)} ${HijriCalendar.gregorianLabel(e.date)}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                if (e.isRange) "${e.hijri.format()} ← ${HijriCalendar.fromGregorian(e.endDate!!, d.adjust).format()}" else e.hijri.format(),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Pill(ArabicFormat.relativeDays(e.daysUntil))
                    }
                    if (e.note.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "foot") {
                Text(
                    "التواريخ بحسب تقويم أم القرى مع تعديلك اليدوي (${adjustLabel(d.adjust)})، وقد تختلف بيومٍ تبعًا لرؤية الهلال.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
    }
}
