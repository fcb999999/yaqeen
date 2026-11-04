package app.yaqeen.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.TaskLink
import app.yaqeen.domain.model.TaskPriority
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

sealed interface TasksUi {
    data object Loading : TasksUi
    data object Failed : TasksUi
    data class Ready(val date: LocalDate, val tasks: List<TodayTask>) : TasksUi
}

class TasksVm(private val c: AppContainer) : ViewModel() {
    private val repo = TasksRepository(c)
    private val _date = MutableStateFlow(c.clock.today())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    val hijriAdjust: StateFlow<Int> =
        c.settings.observe(Settings.HIJRI_ADJUST_DAYS).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<TasksUi> = _date.flatMapLatest { d ->
        c.taskEngine.observeDay(d)
            .map<List<TodayTask>, TasksUi> { TasksUi.Ready(d, it) }
            .catch { emit(TasksUi.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUi.Loading)

    fun today(): LocalDate = c.clock.today()

    /** لا يُسمح بالمستقبل: الأيام السابقة للعرض والتصحيح فقط. */
    fun setDate(d: LocalDate) {
        val t = today()
        _date.value = if (d.isAfter(t)) t else d
    }

    fun shift(days: Long) = setDate(_date.value.plusDays(days))

    fun toggle(t: TodayTask) {
        if (t.derived) return
        val d = _date.value
        viewModelScope.launch { repo.setManualDone(t.task, d, !t.done) }
    }
}

/** يفتح القسم المرتبط بمهمة مشتقة. أي فشل ملاحة لا يُسقط التطبيق. */
fun openLinkedSection(nav: NavHostController, task: app.yaqeen.data.local.TaskEntity) {
    val link = task.linkEnum()
    val route = when (link) {
        TaskLink.PRAYERS, TaskLink.RAWATIB -> Routes.PRAYER
        TaskLink.QURAN_PLAN -> if (task.linkRefId.isBlank()) Routes.quranPlans() else Routes.quranPlans(task.linkRefId)
        else -> adhkarCategoryFor(link)?.let { Routes.adhkarCategory(it) } ?: return
    }
    runCatching { nav.navigate(route) }.onFailure {
        runCatching { nav.navigate(if (link == TaskLink.QURAN_PLAN) Routes.QURAN else Routes.HOME) }
    }
}

@Composable
fun TasksHomeScreen(nav: NavHostController, initialDate: LocalDate? = null) {
    val vm = containerViewModel { TasksVm(it) }
    LaunchedEffect(initialDate) { initialDate?.let { vm.setDate(it) } }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val adjust by vm.hijriAdjust.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            YaqeenTopBar(
                title = "المهام اليومية",
                onBack = { nav.popBackStack() },
                actions = {
                    IconButton(onClick = { nav.navigate(TasksRoutes.daySummary(date)) }) {
                        Icon(Icons.Filled.Assessment, contentDescription = "ملخص اليوم")
                    }
                    IconButton(onClick = { nav.navigate(TasksRoutes.PRESETS) }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAddCheck, contentDescription = "المهام الجاهزة")
                    }
                },
            )
            when (val s = ui) {
                TasksUi.Loading -> LoadingBox()
                TasksUi.Failed -> EmptyState(
                    title = "تعذّر تحميل المهام",
                    message = "حاول مرة أخرى بعد قليل. بياناتك محفوظة على جهازك.",
                    icon = Icons.Filled.Checklist,
                    action = { TextButton(onClick = { vm.setDate(date) }) { Text("إعادة المحاولة") } },
                )
                is TasksUi.Ready -> TasksList(nav, vm, s, adjust)
            }
        }
        ExtendedFloatingActionButton(
            onClick = { nav.navigate(TasksRoutes.editor()) },
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("مهمة جديدة") },
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
        )
    }
}

@Composable
private fun TasksList(nav: NavHostController, vm: TasksVm, s: TasksUi.Ready, adjust: Int) {
    val progress = remember(s.tasks) { DayProgress(s.tasks.size, s.tasks.count { it.done }) }
    val pending = s.tasks.filter { !it.done }
    val done = s.tasks.filter { it.done }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") { ProgressHeader(vm, s.date, progress, adjust) }
        if (s.tasks.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    title = "لا مهام في هذا اليوم",
                    message = "ابدأ بإضافة المهام الجاهزة بنقرة واحدة، أو أنشئ مهمتك الخاصة.",
                    icon = Icons.Filled.Checklist,
                    action = {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { nav.navigate(TasksRoutes.PRESETS) }) { Text("المهام الجاهزة") }
                            TextButton(onClick = { nav.navigate(TasksRoutes.editor()) }) { Text("مهمة جديدة") }
                        }
                    },
                )
            }
        }
        if (pending.isNotEmpty()) {
            item(key = "h_pending") { SectionTitle("لم تُنجز بعد") }
            items(pending, key = { it.task.id }) { t -> TaskRow(nav, vm, t) }
        }
        if (done.isNotEmpty()) {
            item(key = "h_done") { SectionTitle("منجزة") }
            items(done, key = { it.task.id }) { t -> TaskRow(nav, vm, t) }
        }
    }
}

@Composable
private fun ProgressHeader(vm: TasksVm, date: LocalDate, progress: DayProgress, adjust: Int) {
    val today = vm.today()
    val isToday = date == today
    val percent = (progress.ratio * 100).roundToInt()
    YaqeenCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "اليوم السابق") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isToday) "اليوم" else HijriCalendar.dayName(date),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(HijriCalendar.gregorianLabel(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(HijriCalendar.fromGregorian(date, adjust).format(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.shift(1) }, enabled = date.isBefore(today)) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "اليوم التالي")
            }
        }
        if (!isToday) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Pill("يوم سابق: يمكنك تصحيح الإنجاز")
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { vm.setDate(today) }) { Text("العودة إلى اليوم") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (progress.total == 0) "لا مهام" else "المنجز: ${ArabicText.indic(progress.done)} من ${ArabicText.indic(progress.total)}",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text("${ArabicText.indic(percent)}٪", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(progress.ratio)
        if (progress.total > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "المتبقي: ${ArabicFormat.tasks(progress.remaining).takeIf { progress.remaining > 0 } ?: "لا شيء"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            TaskMessages.motivation(progress, date.toEpochDay()),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TaskRow(nav: NavHostController, vm: TasksVm, t: TodayTask) {
    val task = t.task
    val extra = YaqeenTheme.extra
    YaqeenCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = { if (t.derived) openLinkedSection(nav, task) else vm.toggle(t) },
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (t.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = if (t.done) "منجزة" else "غير منجزة",
                    tint = if (t.done) extra.success else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(28.dp),
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null,
                    color = if (t.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                )
                val time = ArabicFormat.time12(task.timeOfDay)
                val high = task.priorityEnum() == TaskPriority.HIGH
                if (t.derived || time != null || high) {
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (t.derived) Pill("تلقائي", container = extra.goldContainer, content = extra.gold)
                        if (time != null) Pill(time)
                        if (high) Pill("أولوية عالية", container = MaterialTheme.colorScheme.tertiaryContainer, content = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
                if (t.detail != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(t.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (t.derived && !t.done) {
                    Text("اضغط لفتح القسم وتسجيل العبادة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
            }
            IconButton(onClick = { nav.navigate(TasksRoutes.editor(task.id)) }) {
                Icon(Icons.Filled.Edit, contentDescription = "تعديل المهمة", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
