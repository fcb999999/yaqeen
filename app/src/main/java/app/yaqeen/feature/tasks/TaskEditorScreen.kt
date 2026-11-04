package app.yaqeen.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.HijriCalendar
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.domain.model.TaskLink
import app.yaqeen.domain.model.TaskPriority
import app.yaqeen.domain.model.TaskRepeat
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek

sealed interface EditorState {
    data object Loading : EditorState
    data object NotFound : EditorState
    data class Editing(val draft: TaskDraft, val isNew: Boolean, val error: String? = null, val saving: Boolean = false) : EditorState
}

class TaskEditorVm(private val c: AppContainer) : ViewModel() {
    private val repo = TasksRepository(c)
    private val _state = MutableStateFlow<EditorState>(EditorState.Loading)
    val state: StateFlow<EditorState> = _state.asStateFlow()
    private var initKey: String? = null

    val plans: StateFlow<List<QuranPlanEntity>> =
        c.db.quranPlans().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun init(id: String, preset: String) {
        val key = "$id|$preset"
        if (initKey == key) return
        initKey = key
        viewModelScope.launch {
            if (id == TasksRoutes.NEW) {
                val p = TaskPreset.fromKey(preset)
                val base = TaskDraft(startDate = c.clock.today())
                _state.value = EditorState.Editing(
                    if (p == null) base else base.copy(title = if (p == TaskPreset.PERSONAL) "" else p.titleAr, link = p.link, preset = p.name),
                    isNew = true,
                )
            } else {
                val t = repo.load(id)
                _state.value = if (t == null) EditorState.NotFound else EditorState.Editing(t.toDraft(), isNew = false)
            }
        }
    }

    fun update(f: (TaskDraft) -> TaskDraft) {
        val s = _state.value as? EditorState.Editing ?: return
        _state.value = s.copy(draft = f(s.draft), error = null)
    }

    fun save(onDone: () -> Unit) {
        val s = _state.value as? EditorState.Editing ?: return
        if (s.saving) return
        val err = validateDraft(s.draft)
        if (err != null) {
            _state.value = s.copy(error = err)
            return
        }
        _state.value = s.copy(saving = true, error = null)
        viewModelScope.launch {
            runCatching { repo.save(s.draft) }
                .onSuccess { onDone() }
                .onFailure { _state.value = s.copy(saving = false, error = "تعذّر الحفظ. حاول مرة أخرى.") }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = (_state.value as? EditorState.Editing)?.draft?.id ?: return
        viewModelScope.launch {
            repo.load(id)?.let { repo.delete(it) }
            onDone()
        }
    }
}

private val WEEK_ORDER = listOf(
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorScreen(nav: NavHostController, id: String, preset: String) {
    val vm = containerViewModel(key = "task-editor-$id-$preset") { TaskEditorVm(it) }
    LaunchedEffect(id, preset) { vm.init(id, preset) }
    val state by vm.state.collectAsStateWithLifecycle()
    val plans by vm.plans.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        when (val s = state) {
            EditorState.Loading -> {
                YaqeenTopBar("المهمة", onBack = { nav.popBackStack() })
                LoadingBox()
            }
            EditorState.NotFound -> {
                YaqeenTopBar("المهمة", onBack = { nav.popBackStack() })
                EmptyState("لم يُعثر على المهمة", "ربما حُذفت من جهاز آخر.", action = { TextButton(onClick = { nav.popBackStack() }) { Text("رجوع") } })
            }
            is EditorState.Editing -> EditorForm(nav, vm, s, plans)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.EditorForm(nav: NavHostController, vm: TaskEditorVm, s: EditorState.Editing, plans: List<QuranPlanEntity>) {
    val d = s.draft
    var showTime by remember { mutableStateOf(false) }
    var showStart by remember { mutableStateOf(false) }
    var showEnd by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    YaqeenTopBar(if (s.isNew) "مهمة جديدة" else "تعديل المهمة", onBack = { nav.popBackStack() })
    Column(
        Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = d.title,
            onValueChange = { v -> vm.update { it.copy(title = v) } },
            label = { Text("عنوان المهمة") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = d.notes,
            onValueChange = { v -> vm.update { it.copy(notes = v) } },
            label = { Text("ملاحظات (اختياري)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        YaqeenCard(Modifier.fillMaxWidth()) {
            SectionTitle("الربط بقسم في التطبيق")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskLink.values().forEach { link ->
                    FilterChip(
                        selected = d.link == link,
                        onClick = { vm.update { it.copy(link = link, linkRefId = if (link == TaskLink.QURAN_PLAN) it.linkRefId else "") } },
                        label = { Text(link.ar) },
                    )
                }
            }
            if (d.link != TaskLink.NONE) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "تُحتسب هذه المهمة تلقائيًا من سجلّك الفعلي في القسم، ولا تُوضع علامة الإنجاز عليها يدويًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (d.link == TaskLink.QURAN_PLAN) {
                Spacer(Modifier.height(8.dp))
                Text("الورد", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = d.linkRefId.isBlank(), onClick = { vm.update { it.copy(linkRefId = "") } }, label = { Text("أي ورد نشط") })
                    plans.forEach { p ->
                        FilterChip(selected = d.linkRefId == p.id, onClick = { vm.update { it.copy(linkRefId = p.id) } }, label = { Text(p.name) })
                    }
                }
            }
        }

        YaqeenCard(Modifier.fillMaxWidth()) {
            SectionTitle("الوقت والأولوية")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "الوقت: " + (ArabicFormat.time12(d.timeOfDay) ?: "غير محدد"),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { showTime = true }) { Text(if (d.timeOfDay == null) "تحديد" else "تغيير") }
                if (d.timeOfDay != null) TextButton(onClick = { vm.update { it.copy(timeOfDay = null, reminderEnabled = false) } }) { Text("إزالة") }
            }
            Spacer(Modifier.height(8.dp))
            Text("الأولوية", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskPriority.values().forEach { p ->
                    FilterChip(selected = d.priority == p, onClick = { vm.update { it.copy(priority = p) } }, label = { Text(p.ar) })
                }
            }
            SwitchRow(
                title = "تذكير بالمهمة",
                subtitle = if (d.timeOfDay == null) "حدّد وقت المهمة أولًا لتفعيل التذكير" else "تنبيه في الوقت المحدد إن لم تكن المهمة منجزة",
                checked = d.reminderEnabled && d.timeOfDay != null,
                onCheckedChange = { on -> if (d.timeOfDay != null) vm.update { it.copy(reminderEnabled = on) } },
            )
        }

        YaqeenCard(Modifier.fillMaxWidth()) {
            SectionTitle("التكرار")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TaskRepeat.values().forEach { r ->
                    val label = when (r) {
                        TaskRepeat.NONE -> "مرة واحدة"
                        TaskRepeat.DAILY -> "كل يوم"
                        TaskRepeat.WEEKLY -> "أيام محددة من الأسبوع"
                    }
                    FilterChip(selected = d.repeat == r, onClick = { vm.update { it.copy(repeat = r) } }, label = { Text(label) })
                }
            }
            if (d.repeat == TaskRepeat.WEEKLY) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WEEK_ORDER.forEach { dow ->
                        val bit = 1 shl (dow.value - 1)
                        FilterChip(
                            selected = (d.daysMask and bit) != 0,
                            onClick = { vm.update { it.copy(daysMask = it.daysMask xor bit) } },
                            label = { Text(HijriCalendar.DAY_NAMES.getValue(dow)) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (d.repeat == TaskRepeat.NONE) "التاريخ: " else "تبدأ من: ") + HijriCalendar.gregorianLabel(d.startDate),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = { showStart = true }) { Text("تغيير") }
            }
            if (d.repeat != TaskRepeat.NONE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "تنتهي في: " + (d.endDate?.let { HijriCalendar.gregorianLabel(it) } ?: "بلا نهاية"),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = { showEnd = true }) { Text(if (d.endDate == null) "تحديد" else "تغيير") }
                    if (d.endDate != null) TextButton(onClick = { vm.update { it.copy(endDate = null) } }) { Text("إزالة") }
                }
            }
        }

        if (s.error != null) {
            Text(s.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        Button(onClick = { vm.save { nav.popBackStack() } }, enabled = !s.saving, modifier = Modifier.fillMaxWidth()) {
            Text(if (s.isNew) "إضافة المهمة" else "حفظ التعديلات")
        }
        if (!s.isNew) {
            OutlinedButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("حذف المهمة", color = MaterialTheme.colorScheme.error)
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showTime) TimePickerDialogAr(d.timeOfDay, onDismiss = { showTime = false }, onPick = { t -> vm.update { it.copy(timeOfDay = t) } })
    if (showStart) DatePickerDialogAr(d.startDate, onDismiss = { showStart = false }, onPick = { dt -> vm.update { it.copy(startDate = dt) } })
    if (showEnd) DatePickerDialogAr(d.endDate ?: d.startDate, onDismiss = { showEnd = false }, onPick = { dt -> vm.update { it.copy(endDate = dt) } })
    if (showDelete) {
        ConfirmDialog(
            title = "حذف المهمة",
            message = "ستُحذف المهمة من كل الأيام (سجلّات العبادة نفسها لا تتأثر). هل تريد المتابعة؟",
            confirmText = "حذف",
            onConfirm = { vm.delete { nav.popBackStack() } },
            onDismiss = { showDelete = false },
        )
    }
}
