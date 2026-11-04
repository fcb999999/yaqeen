package app.yaqeen.feature.quran.plans

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.core.dayKey
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.domain.model.QuranPlanStatus
import app.yaqeen.ui.components.ConfirmDialog
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.ProgressRing
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

internal class PlansViewModel(private val c: AppContainer) : ViewModel() {
    val repo = PlanRepository.of(c)
    val plans: StateFlow<List<QuranPlanEntity>?> =
        c.db.quranPlans().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    var math by mutableStateOf<PlanMath?>(null)
        private set

    init {
        viewModelScope.launch { math = repo.math() }
    }

    fun logs(planId: String) = c.db.quranPlanLogs().observeForPlan(planId)
    fun todayLogs(date: String) = c.db.quranPlanLogs().observeRange(date, date)
    fun run(block: suspend PlanRepository.() -> Unit) { viewModelScope.launch { repo.block() } }
}

@Composable
internal fun rememberToday(c: AppContainer): LocalDate {
    var d by remember { mutableStateOf(c.clock.today()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            d = c.clock.today()
        }
    }
    return d
}

private data class EditTarget(val plan: QuranPlanEntity?)

@Composable
internal fun PlansRoot(nav: NavHostController, c: AppContainer, planId: String?) {
    val vm = containerViewModel { PlansViewModel(it) }
    val plans by vm.plans.collectAsState()
    val math = vm.math
    val today = rememberToday(c)
    val startedFromList = remember { planId.isNullOrBlank() }
    var detailId by rememberSaveable { mutableStateOf(planId?.takeIf { it.isNotBlank() }) }
    var editing by remember { mutableStateOf<EditTarget?>(null) }
    LaunchedEffect(planId) { if (!planId.isNullOrBlank()) detailId = planId }

    BackHandler(enabled = editing != null) { editing = null }
    BackHandler(enabled = editing == null && detailId != null && startedFromList) { detailId = null }

    when {
        plans == null || math == null -> Column(Modifier.fillMaxSize()) { YaqeenTopBar("الأوراد", onBack = { nav.popBackStack() }); LoadingBox() }
        editing != null -> PlanEditorScreen(
            c = c, math = math, existing = editing!!.plan, today = today,
            onClose = { editing = null },
            onSaved = { id -> editing = null; detailId = id },
        )
        detailId != null -> {
            val plan = plans!!.firstOrNull { it.id == detailId }
            if (plan == null) {
                Column(Modifier.fillMaxSize()) {
                    YaqeenTopBar("الورد", onBack = { if (startedFromList) detailId = null else nav.popBackStack() })
                    EmptyState("لم يُعثر على هذا الورد", "ربما حُذف من جهاز آخر.")
                }
            } else {
                PlanDetailScreen(
                    nav = nav, c = c, vm = vm, math = math, plan = plan, today = today,
                    onBack = { if (startedFromList) detailId = null else nav.popBackStack() },
                    onEdit = { editing = EditTarget(plan) },
                    onDeleted = { if (startedFromList) detailId = null else nav.popBackStack() },
                )
            }
        }
        else -> PlansListScreen(
            nav = nav, vm = vm, math = math, plans = plans!!, today = today,
            onOpen = { detailId = it.id },
            onNew = { editing = EditTarget(null) },
        )
    }
}

// ------------------------------------------------------------------ القائمة

@Composable
private fun PlansListScreen(
    nav: NavHostController, vm: PlansViewModel, math: PlanMath, plans: List<QuranPlanEntity>, today: LocalDate,
    onOpen: (QuranPlanEntity) -> Unit, onNew: () -> Unit,
) {
    val todayLogs by vm.todayLogs(today.dayKey()).collectAsState(initial = emptyList())
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { YaqeenTopBar("الأوراد القرآنية", onBack = { nav.popBackStack() }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("ورد جديد") })
        },
    ) { pad ->
        if (plans.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    title = "لا توجد أوراد بعد",
                    message = "أنشئ وردًا بتحديد المدى والمدة والمقدار اليومي، وسيتتبّع يقين تقدّمك أثناء قراءتك في المصحف.",
                    icon = Icons.Filled.MenuBook,
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(pad).fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(plans, key = { it.id }) { plan ->
                    PlanCard(plan, math, todayLogs.firstOrNull { it.planId == plan.id }, today) { onOpen(plan) }
                }
            }
        }
    }
}

@Composable
private fun statusLabel(plan: QuranPlanEntity, today: LocalDate): Pair<String, Boolean> = when {
    plan.status == QuranPlanStatus.COMPLETED.name -> "مكتمل" to true
    plan.status == QuranPlanStatus.PAUSED.name -> "متوقف مؤقتًا" to false
    plan.startDate > today.dayKey() -> "لم يبدأ بعد" to false
    else -> "نشط" to true
}

@Composable
private fun PlanCard(plan: QuranPlanEntity, math: PlanMath, todayLog: QuranPlanLogEntity?, today: LocalDate, onClick: () -> Unit) {
    val fraction = math.fraction(plan)
    val unit = plan.unitEnum()
    val (label, good) = statusLabel(plan, today)
    YaqeenCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction, size = 64.dp, stroke = 7.dp, label = "${ArabicText.indic((fraction * 100).toInt())}٪")
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Pill(
                        label,
                        container = if (good) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                        content = if (good) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                if (plan.status == QuranPlanStatus.COMPLETED.name) {
                    Text("تمّ الورد بحمد الله", style = MaterialTheme.typography.bodyMedium, color = YaqeenTheme.extra.success)
                } else {
                    val portion = math.todayPortion(plan, todayLog)
                    if (portion != null) {
                        Text("ورد اليوم: ${math.rangeLabel(portion)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        "المتبقي: ${PlanMath.unitText(unit, math.remainingUnits(plan))}" +
                            (PlanMath.daysRemaining(today, plan.endDate?.let(LocalDate::parse))?.let { " • ${ArabicText.indic(it)} يومًا" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------ التفاصيل

@Composable
private fun PlanDetailScreen(
    nav: NavHostController, c: AppContainer, vm: PlansViewModel, math: PlanMath, plan: QuranPlanEntity, today: LocalDate,
    onBack: () -> Unit, onEdit: () -> Unit, onDeleted: () -> Unit,
) {
    val logs by vm.logs(plan.id).collectAsState(initial = emptyList())
    val todayKey = today.dayKey()
    val todayLog = logs.firstOrNull { it.date == todayKey }
    val unit = plan.unitEnum()
    val start = LocalDate.parse(plan.startDate)
    val end = plan.endDate?.let(LocalDate::parse)
    val completed = plan.status == QuranPlanStatus.COMPLETED.name
    val paused = plan.status == QuranPlanStatus.PAUSED.name
    val notStarted = start.isAfter(today)
    val portion = if (completed) null else math.todayPortion(plan, todayLog)
    val met = completed || (todayLog?.targetMet == true)
    val fraction = math.fraction(plan)
    val remainingUnits = math.remainingUnits(plan)
    val daysLeft = PlanMath.daysRemaining(today, end)
    val required = PlanMath.requiredDaily(remainingUnits, daysLeft)
    val logMap = remember(logs) { logs.associateBy { it.date } }
    val dates = remember(logs, plan.startDate, plan.endDate, todayKey) {
        PlanMath.historyDates(start, end, today, logs.map { LocalDate.parse(it.date) })
    }
    val metDays = remember(logs) { logs.count { it.targetMet } }

    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }
    var showPosition by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar(plan.name, onBack = onBack, actions = {
            IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, "تعديل الورد") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "المزيد") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!completed) {
                        DropdownMenuItem(
                            text = { Text(if (paused) "استئناف الورد" else "إيقاف مؤقت") },
                            onClick = { menu = false; vm.run { setStatus(plan.id, if (paused) QuranPlanStatus.ACTIVE else QuranPlanStatus.PAUSED) } },
                        )
                        DropdownMenuItem(text = { Text("ضبط موضع التقدّم") }, onClick = { menu = false; showPosition = true })
                        DropdownMenuItem(text = { Text("إتمام الورد") }, onClick = { menu = false; confirmComplete = true })
                    } else {
                        DropdownMenuItem(text = { Text("ضبط موضع التقدّم") }, onClick = { menu = false; showPosition = true })
                    }
                    DropdownMenuItem(text = { Text("حذف الورد") }, onClick = { menu = false; confirmDelete = true })
                }
            }
        })
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // التقدم الكلي
            item("progress") {
                YaqeenCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProgressRing(fraction, size = 92.dp, stroke = 9.dp, label = "${ArabicText.indic((fraction * 100).toInt())}٪")
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val (label, good) = statusLabel(plan, today)
                            Pill(
                                label,
                                container = if (good) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                content = if (good) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "من ${math.ayahLabel(plan.startAyahId)} إلى ${math.ayahLabel(plan.endAyahId)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "المتبقي: ${PlanMath.unitText(unit, remainingUnits)}",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            if (end != null) {
                                Text(
                                    if (daysLeft != null && daysLeft > 0) "تبقّى ${ArabicText.indic(daysLeft)} يومًا حتى ${PlanFormat.dateShort(end)}"
                                    else "المدّة المحددة (${PlanFormat.dateShort(end)}) انقضت",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ProgressBar(fraction)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "المقدار اليومي: ${PlanMath.unitText(unit, plan.dailyAmount)}" +
                            (if (required != null && required > plan.dailyAmount && !completed) " • لإنهائه في موعده يلزم ${PlanMath.unitText(unit, required)} يوميًا" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ورد اليوم
            item("today") {
                YaqeenCard(Modifier.fillMaxWidth()) {
                    SectionTitle("ورد اليوم")
                    when {
                        completed -> Text("تمّ الورد بحمد الله. تقبّل الله منك.", style = MaterialTheme.typography.bodyLarge, color = YaqeenTheme.extra.success)
                        paused -> Text("الورد متوقف مؤقتًا. استأنفه من قائمة الخيارات ليعود التتبّع والتذكير.", style = MaterialTheme.typography.bodyMedium)
                        notStarted -> Text("يبدأ هذا الورد في ${PlanFormat.dateLong(start)}.", style = MaterialTheme.typography.bodyMedium)
                        portion == null -> Text("لا يوجد مقطع متبقٍّ.", style = MaterialTheme.typography.bodyMedium)
                        else -> {
                            Text(math.rangeLabel(portion), style = MaterialTheme.typography.titleLarge)
                            Text(
                                math.pagesLabel(portion.fromAyah, portion.toAyah) + " • " + PlanMath.unitText(unit, plan.dailyAmount),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!completed && !paused && !notStarted && todayLog != null && (todayLog.ayahsRead > 0)) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "قرأتَ اليوم ${PlanMath.unitText(app.yaqeen.domain.model.QuranPlanUnit.PAGE, todayLog.pagesRead.coerceAtLeast(1))} (${ArabicText.indic(todayLog.ayahsRead)} آية)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!completed && !paused && !notStarted) {
                        Spacer(Modifier.height(12.dp))
                        if (met) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, null, tint = YaqeenTheme.extra.success)
                                Spacer(Modifier.width(8.dp))
                                Text("أتممتَ ورد اليوم، ما شاء الله", style = MaterialTheme.typography.titleSmall, color = YaqeenTheme.extra.success, modifier = Modifier.weight(1f))
                                TextButton(onClick = { vm.run { undoToday(plan.id) } }) { Text("تراجع") }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    val target = math.q.ayah((plan.progressAyahId + 1).coerceIn(plan.startAyahId, plan.endAyahId))
                                    nav.navigate(Routes.quranReader(surah = target.surah, ayah = target.number))
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("اقرأ الآن") }
                            if (!met) {
                                OutlinedButton(onClick = { vm.run { markTodayDone(plan.id) } }, modifier = Modifier.weight(1f)) { Text("تمّ ورد اليوم") }
                            }
                        }
                    }
                }
            }

            // التذكير
            item("reminder") {
                YaqeenCard(Modifier.fillMaxWidth()) {
                    SwitchRow(
                        title = "تذكير يومي بالورد",
                        checked = plan.reminderEnabled,
                        onCheckedChange = { on ->
                            if (on && plan.reminderTime == null) showTime = true
                            else vm.run { setReminder(plan.id, on, plan.reminderTime) }
                        },
                        subtitle = plan.reminderTime?.let { "الوقت: ${PlanFormat.time12(it)}" } ?: "لم يُحدَّد وقت بعد",
                    )
                    if (plan.reminderEnabled || plan.reminderTime != null) {
                        TextButton(onClick = { showTime = true }) { Text("تغيير وقت التذكير") }
                    }
                }
            }

            // سجل الالتزام
            item("history-title") {
                SectionTitle("سجل الأيام")
                Text(
                    "أيام أُتمّ فيها الورد: ${ArabicText.indic(metDays)}. الأيام غير المسجّلة لا تُعدّ تقصيرًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(dates, key = { "d$it" }) { d ->
                HistoryRow(d, logMap[d.dayKey()], d == today)
            }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "حذف الورد",
            message = "سيُحذف «${plan.name}» مع سجلّه اليومي. لا يمكن التراجع عن ذلك.",
            confirmText = "حذف",
            onConfirm = { vm.run { delete(plan.id) }; onDeleted() },
            onDismiss = { confirmDelete = false },
        )
    }
    if (confirmComplete) {
        ConfirmDialog(
            title = "إتمام الورد",
            message = "سيُعلَّم الورد مكتملًا ويُعدّ التقدم إلى نهايته. هل أتممتَه فعلًا؟",
            confirmText = "نعم، أتممته",
            onConfirm = { vm.run { complete(plan.id) } },
            onDismiss = { confirmComplete = false },
        )
    }
    if (showTime) {
        WirdTimeDialog(
            initial = plan.reminderTime,
            onConfirm = { t -> showTime = false; vm.run { setReminder(plan.id, true, t) } },
            onDismiss = { showTime = false },
        )
    }
    if (showPosition) {
        SetPositionDialog(
            math = math, plan = plan,
            onConfirm = { ayah -> showPosition = false; vm.run { setProgress(plan.id, ayah) } },
            onDismiss = { showPosition = false },
        )
    }
}

@Composable
private fun HistoryRow(date: LocalDate, log: QuranPlanLogEntity?, isToday: Boolean) {
    val status = PlanMath.dayStatus(log)
    val (text, color) = when (status) {
        DayStatus.MET -> "أُتمّ الورد" to YaqeenTheme.extra.success
        DayStatus.PARTIAL -> "قراءة جزئية" to YaqeenTheme.extra.warning
        DayStatus.UNRECORDED -> (if (isToday) "لم يُسجَّل بعد" else "غير مسجّل") to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (isToday) "اليوم" else PlanFormat.dateLong(date), style = MaterialTheme.typography.bodyMedium)
                if (log != null && (log.ayahsRead > 0 || log.pagesRead > 0)) {
                    Text(
                        "${PlanMath.unitText(app.yaqeen.domain.model.QuranPlanUnit.PAGE, log.pagesRead.coerceAtLeast(1))} • ${ArabicText.indic(log.ayahsRead)} آية",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(text, style = MaterialTheme.typography.labelLarge, color = color, textAlign = TextAlign.End)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
