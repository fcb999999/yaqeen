package app.yaqeen.feature.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.feature.tasks.ArabicFormat
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.theme.YaqeenTheme
import java.time.LocalDate

@Composable
fun CalendarHomeScreen(nav: NavHostController) {
    val vm = containerViewModel { CalendarVm(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val mode by vm.mode.collectAsStateWithLifecycle()
    val adjust by vm.adjust.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var showAdjust by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar(
            title = "التقويم",
            onBack = { nav.popBackStack() },
            actions = {
                IconButton(onClick = { vm.goToday() }) { Icon(Icons.Filled.Today, contentDescription = "اليوم") }
                IconButton(onClick = { nav.navigate(CalendarRoutes.EVENTS) }) { Icon(Icons.Filled.Event, contentDescription = "المناسبات") }
            },
        )
        when (val s = ui) {
            CalendarUi.Loading -> LoadingBox()
            CalendarUi.Failed -> EmptyState("تعذّر تحميل التقويم", "حاول مرة أخرى بعد قليل.", Icons.Filled.CalendarMonth)
            is CalendarUi.Ready -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MonthHeader(s.month.grid, mode, onPrev = { vm.shiftMonth(-1) }, onNext = { vm.shiftMonth(1) },
                    onTitle = { showPicker = true }, onMode = { vm.setMode(it) })
                MonthGridCard(s.month, mode) { d -> nav.navigate(CalendarRoutes.day(d)) }
                Legend()
                StatsCard(s.month)
                MonthEventsCard(nav, s.month)
                AdjustCard(adjust) { showAdjust = true }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showPicker) {
        MonthYearPickerDialog(mode, vm.currentKey(), onDismiss = { showPicker = false }, onPick = { vm.goTo(it) })
    }
    if (showAdjust) {
        HijriAdjustDialog(adjust, onDismiss = { showAdjust = false }, onPick = { vm.setAdjust(it) })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthHeader(
    grid: MonthGrid,
    mode: CalendarMode,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTitle: () -> Unit,
    onMode: (CalendarMode) -> Unit,
) {
    YaqeenCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "الشهر السابق") }
            Column(
                Modifier.weight(1f).clickable(onClick = onTitle).padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(grid.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Text(grid.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("اضغط للانتقال إلى شهر أو سنة", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "الشهر التالي") }
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            Text("التقويم الأساسي:", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
            FilterChip(selected = mode == CalendarMode.HIJRI, onClick = { onMode(CalendarMode.HIJRI) }, label = { Text("هجري") })
            FilterChip(selected = mode == CalendarMode.GREGORIAN, onClick = { onMode(CalendarMode.GREGORIAN) }, label = { Text("ميلادي") })
        }
    }
}

@Composable
private fun MonthGridCard(m: MonthUi, mode: CalendarMode, onDay: (LocalDate) -> Unit) {
    val hijriDays = remember(m.grid, m.adjust) { m.grid.days.map { HijriCalendar.fromGregorian(it, m.adjust).day } }
    val cells: List<Int?> = remember(m.grid) {
        val list = MutableList<Int?>(m.grid.leadingBlanks) { null } + m.grid.days.indices
        val pad = (7 - list.size % 7) % 7
        list + List<Int?>(pad) { null }
    }
    YaqeenCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            CalendarMath.WEEK_HEADERS.forEach { h ->
                Text(
                    h, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { idx ->
                    if (idx == null) {
                        Spacer(Modifier.weight(1f))
                    } else {
                        val date = m.grid.days[idx]
                        DayCell(
                            date = date,
                            hijriDay = hijriDays[idx],
                            mode = mode,
                            ui = m,
                            modifier = Modifier.weight(1f),
                            onClick = { onDay(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, hijriDay: Int, mode: CalendarMode, ui: MonthUi, modifier: Modifier, onClick: () -> Unit) {
    val isToday = date == ui.today
    val ind = ui.indicators[date.toString()]
    val personal = date.toString() in ui.personalDates
    val islamic = ui.islamicByDate[date].orEmpty()
    val primary = if (mode == CalendarMode.HIJRI) hijriDay else date.dayOfMonth
    val secondary = if (mode == CalendarMode.HIJRI) date.dayOfMonth else hijriDay
    val extra = YaqeenTheme.extra
    val desc = buildString {
        append(HijriCalendar.dayName(date)).append(" ").append(HijriCalendar.gregorianLabel(date))
        if (ind != null) append("، يوجد تسجيل")
        if (personal) append("، حدث شخصي")
        if (islamic.isNotEmpty()) append("، ").append(islamic.first().nameAr)
    }
    val shape = MaterialTheme.shapes.small
    Box(
        modifier
            .padding(2.dp)
            .aspectRatio(0.82f)
            .clip(shape)
            .background(if (isToday) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .then(if (isToday) Modifier.border(BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary), shape) else Modifier)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = desc },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(ArabicText.indic(primary), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            Text(ArabicText.indic(secondary), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.height(6.dp)) {
                if (ind != null) {
                    val a = when (ind.categories) { 1 -> 0.4f; 2 -> 0.65f; else -> 1f }
                    Dot(MaterialTheme.colorScheme.primary, Modifier.alpha(a))
                }
                if (personal) Dot(MaterialTheme.colorScheme.tertiary)
                if (islamic.isNotEmpty()) Dot(extra.gold)
            }
        }
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).clip(CircleShape).background(color))
}

@Composable
private fun Legend() {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(MaterialTheme.colorScheme.primary, "تسجيلات")
        LegendItem(MaterialTheme.colorScheme.tertiary, "حدث شخصي")
        LegendItem(YaqeenTheme.extra.gold, "مناسبة إسلامية")
    }
}

@Composable
private fun LegendItem(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatsCard(m: MonthUi) {
    val s = m.stats
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("حصيلة الشهر")
        Text(s.commitmentText(), style = MaterialTheme.typography.bodyLarge)
        if (s.daysWithRecord > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                buildList {
                    if (s.prayersRecorded > 0) add("صلوات مسجّلة: ${ArabicText.indic(s.prayersRecorded)}")
                    if (s.adhkarDays > 0) add("أيام فيها أذكار: ${ArabicText.indic(s.adhkarDays)}")
                    if (s.wirdDays > 0) add("أيام فيها قراءة ورد: ${ArabicText.indic(s.wirdDays)}")
                    if (s.tasksDone > 0) add("مهام منجزة يدويًا: ${ArabicText.indic(s.tasksDone)}")
                }.joinToString(" – "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "يُحتسب ما سُجِّل فقط؛ وما لم يُسجَّل لا يعني أنه لم يحدث.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun MonthEventsCard(nav: NavHostController, m: MonthUi) {
    val events = m.islamicByDate.values.flatten().sortedBy { it.date }
        .filter { it.key != IslamicEventKeys.SACRED_MONTH }
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("مناسبات هذا الشهر")
        if (events.isEmpty()) {
            Text("لا مناسبات في هذا الشهر.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            events.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { nav.navigate(CalendarRoutes.day(e.date)) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.nameAr, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${HijriCalendar.dayName(e.date)} ${HijriCalendar.gregorianLabel(e.date)} – ${e.hijri.format()}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Pill(ArabicFormat.relativeDays(java.time.temporal.ChronoUnit.DAYS.between(m.today, e.date).toInt()))
                }
            }
        }
        TextButton(onClick = { nav.navigate(CalendarRoutes.EVENTS) }) { Text("كل المناسبات القادمة") }
    }
}

@Composable
private fun AdjustCard(adjust: Int, onEdit: () -> Unit) {
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("ضبط التاريخ الهجري")
        Text(
            "التاريخ الهجري محسوب بتقويم أم القرى، وقد يختلف عن رؤية الهلال في بلدك بيوم. يمكنك تعديله يدويًا من يومين قبل إلى يومين بعد.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("التعديل الحالي: " + adjustLabel(adjust), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = onEdit) { Text("تعديل") }
        }
    }
}

fun adjustLabel(v: Int): String = when (v) {
    0 -> "بلا تعديل"
    1 -> "+١ يوم"
    2 -> "+٢ يومان"
    -1 -> "−١ يوم"
    else -> "−٢ يومان"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HijriAdjustDialog(adjust: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ضبط التاريخ الهجري") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "اعتمد التطبيق تقويم أم القرى. إن أُعلن دخول الشهر في بلدك بيومٍ يخالف التقويم، فاضبط الفرق هنا (+١ يقدّم التاريخ الهجري يومًا، −١ يؤخّره). يؤثر هذا على التقويم والمناسبات وتنبيهاتها.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (-2..2).forEach { v ->
                        FilterChip(selected = adjust == v, onClick = { onPick(v) }, label = { Text(adjustLabel(v)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("تم") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthYearPickerDialog(mode: CalendarMode, current: MonthKey, onDismiss: () -> Unit, onPick: (MonthKey) -> Unit) {
    var year by remember { mutableStateOf(current.year) }
    val (lo, hi) = if (mode == CalendarMode.GREGORIAN) CalendarMath.MIN_GREGORIAN_YEAR to CalendarMath.MAX_GREGORIAN_YEAR
    else CalendarMath.MIN_HIJRI_YEAR to CalendarMath.MAX_HIJRI_YEAR
    val names = if (mode == CalendarMode.GREGORIAN) HijriCalendar.GREGORIAN_MONTHS else HijriCalendar.MONTH_NAMES
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("الانتقال إلى شهر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = { if (year > lo) year-- }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "السنة السابقة") }
                    Text(
                        ArabicText.indic(year) + if (mode == CalendarMode.HIJRI) " هـ" else "",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    IconButton(onClick = { if (year < hi) year++ }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "السنة التالية") }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    names.forEachIndexed { i, n ->
                        FilterChip(
                            selected = year == current.year && i + 1 == current.month,
                            onClick = { onPick(MonthKey(year, i + 1)); onDismiss() },
                            label = { Text(n) },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
