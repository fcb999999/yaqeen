package app.yaqeen.feature.prayer.tracking

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.Sunnah
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.ProgressRing
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import java.time.LocalDate

/** شاشة إحصاءات الصلاة والسنن (Routes.PRAYER_STATS). */
@Composable
fun PrayerStatsScreen(nav: NavHostController, @Suppress("UNUSED_PARAMETER") c: AppContainer) {
    val vm = containerViewModel { PrayerStatsViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("إحصاءات الصلاة", onBack = { nav.popBackStack() })
        when {
            ui.loading -> LoadingBox()
            ui.error -> EmptyState(
                title = "تعذّر تحميل الإحصاءات",
                message = "حدثت مشكلة أثناء القراءة من الجهاز.",
                action = { TextButton(onClick = vm::retry) { Text("إعادة المحاولة") } },
            )
            else -> StatsContent(ui, vm, onEditDay = { editingDate = it })
        }
    }

    editingDate?.let { d ->
        PrayerDayEditorSheet(repo = vm.repo, date = d, today = ui.today, onDismiss = { editingDate = null })
    }
}

@Composable
private fun StatsContent(ui: PrayerStatsUi, vm: PrayerStatsViewModel, onEditDay: (LocalDate) -> Unit) {
    val prayer = ui.prayer ?: return
    val rawatib = ui.rawatib ?: return
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "periods") { PeriodChips(ui.period, vm::setPeriod) }
        item(key = "navigator") { PeriodNavigator(ui, vm::previous, vm::next) }
        item(key = "summary") { SummaryCard(prayer) }
        if (ui.period != StatsPeriod.DAY) {
            item(key = "chart") { ChartCard(ui, prayer) }
        }
        item(key = "per_prayer") { PerPrayerCard(prayer) }
        item(key = "rawatib") { RawatibStatsCard(ui, rawatib) }

        item(key = "history_title") { SectionTitle("سجل الأيام") }
        item(key = "history_hint") {
            Text(
                "انقر على أي يوم لتصحيح التسجيل. الدائرة الفارغة تعني أن الصلاة لم تُسجَّل، وهذا لا يعني أنك لم تصلِّها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val days = prayer.perDay.asReversed()
        if (days.isEmpty()) {
            item(key = "history_empty") { EmptyState(title = "لا أيام في هذه الفترة بعد") }
        }
        items(days, key = { it.date.toString() }) { day ->
            HistoryRow(day, ui.today, rawatib, onClick = { onEditDay(day.date) })
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

// ------------------------------------------------------------------ اختيار الفترة

@Composable
private fun PeriodChips(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatsPeriod.entries.forEach { p ->
            FilterChip(selected = p == selected, onClick = { onSelect(p) }, label = { Text(p.ar) })
        }
    }
}

private fun rangeLabel(ui: PrayerStatsUi): String = when (ui.period) {
    StatsPeriod.DAY -> PrayerFormat.dayLabel(ui.range.from, ui.today)
    StatsPeriod.WEEK -> if (ui.range.from.month == ui.range.to.month) {
        "${ArabicText.indic(ui.range.from.dayOfMonth)} – ${ArabicText.indic(ui.range.to.dayOfMonth)} ${HijriCalendar.GREGORIAN_MONTHS[ui.range.to.monthValue - 1]}"
    } else {
        "${ArabicText.indic(ui.range.from.dayOfMonth)} ${HijriCalendar.GREGORIAN_MONTHS[ui.range.from.monthValue - 1]} – " +
            "${ArabicText.indic(ui.range.to.dayOfMonth)} ${HijriCalendar.GREGORIAN_MONTHS[ui.range.to.monthValue - 1]}"
    }
    StatsPeriod.MONTH -> "${HijriCalendar.GREGORIAN_MONTHS[ui.range.from.monthValue - 1]} ${ArabicText.indic(ui.range.from.year)}"
    StatsPeriod.LAST_7 -> "آخر ٧ أيام"
    StatsPeriod.LAST_30 -> "آخر ٣٠ يومًا"
    StatsPeriod.LAST_90 -> "آخر ٩٠ يومًا"
}

@Composable
private fun PeriodNavigator(ui: PrayerStatsUi, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (ui.canNavigate) {
            IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "الفترة السابقة") }
        } else {
            Spacer(Modifier.width(48.dp))
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(rangeLabel(ui), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (ui.period.isRolling) {
                Text(
                    "${PrayerFormat.shortDate(ui.range.from, ui.today)} – ${PrayerFormat.shortDate(ui.range.to, ui.today)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (ui.canNavigate) {
            IconButton(onClick = onNext, enabled = ui.canGoNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "الفترة التالية") }
        } else {
            Spacer(Modifier.width(48.dp))
        }
    }
}

// ------------------------------------------------------------------ الملخص

@Composable
private fun SummaryCard(s: PrayerRangeStats) {
    YaqeenCard(Modifier.fillMaxWidth()) {
        if (s.days == 0) {
            Text("لا أيام في هذه الفترة بعد.", style = MaterialTheme.typography.bodyMedium)
            return@YaqeenCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PrayerFormat.coverage(s.recorded, s.possible), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                ProgressBar(s.coverage)
                Spacer(Modifier.height(6.dp))
                Text(
                    "التسجيل اختياري؛ والصلاة غير المسجّلة لا تُحتسب تقصيرًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                ProgressRing(
                    progress = s.jamaaRatio ?: 0f,
                    size = 76.dp,
                    label = s.jamaaPercent?.let { PrayerFormat.percent(it) } ?: "—",
                )
                Spacer(Modifier.height(4.dp))
                Text("نسبة الجماعة", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(s.mosque, "في المسجد جماعة", statusAccent(PrayerStatus.MOSQUE), Modifier.weight(1f))
            StatTile(s.home, "في البيت", statusAccent(PrayerStatus.HOME), Modifier.weight(1f))
            StatTile(s.notPrayed, "لم أصلِّ بعد", statusAccent(PrayerStatus.NOT_PRAYED), Modifier.weight(1f))
            StatTile(s.unrecorded, "غير مسجّلة", statusAccent(null), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Text(
            if (s.jamaaPercent != null) {
                "نسبة الجماعة = الصلوات في المسجد ÷ (المسجد + البيت) من الصلوات المؤداة المسجّلة."
            } else {
                "تظهر نسبة الجماعة بعد تسجيل صلاة مؤداة واحدة على الأقل."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(PrayerFormat.encouragement(s), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun StatTile(value: Int, label: String, accent: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ArabicText.indic(value), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        androidx.compose.foundation.Canvas(Modifier.width(24.dp).height(3.dp)) { drawRect(accent) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ------------------------------------------------------------------ الرسم

@Composable
private fun ChartCard(ui: PrayerStatsUi, s: PrayerRangeStats) {
    val dates = remember(ui.range) { generateSequence(ui.range.from) { it.plusDays(1) }.takeWhile { !it.isAfter(ui.range.to) }.toList() }
    val byDate = remember(s) { s.perDay.associateBy { it.date } }
    YaqeenCard(Modifier.fillMaxWidth()) {
        val weekly = dates.size <= 7
        Text(if (weekly) "الصلوات المسجّلة في كل يوم" else "الأيام", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        if (weekly) {
            WeeklyBars(dates, byDate, ui.today)
            Spacer(Modifier.height(12.dp))
            ChartLegend(
                listOf(
                    "في المسجد" to statusAccent(PrayerStatus.MOSQUE),
                    "في البيت" to statusAccent(PrayerStatus.HOME),
                    "لم أصلِّ بعد" to statusAccent(PrayerStatus.NOT_PRAYED),
                ),
            )
        } else {
            DayStrip(dates, byDate, ui.today)
            Spacer(Modifier.height(12.dp))
            ChartLegend(
                listOf(
                    "بلا تسجيل" to MaterialTheme.colorScheme.surfaceVariant,
                    "بعض الصلوات" to MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    "الصلوات الخمس" to MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "كل خلية يوم؛ وكلما قوي لونها زادت الصلوات المؤداة المسجّلة فيه.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PerPrayerCard(s: PrayerRangeStats) {
    YaqeenCard(Modifier.fillMaxWidth()) {
        Text("تفصيل كل صلاة", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val mosque = statusAccent(PrayerStatus.MOSQUE)
        val home = statusAccent(PrayerStatus.HOME)
        val notPrayed = statusAccent(PrayerStatus.NOT_PRAYED)
        val unrecorded = MaterialTheme.colorScheme.surfaceVariant
        s.perPrayer.forEach { b ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.prayer.ar, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        "سُجّلت ${ArabicText.indic(b.recorded)} من ${ArabicText.indic(s.days)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                StackedBar(listOf(b.mosque to mosque, b.home to home, b.notPrayed to notPrayed, b.unrecorded to unrecorded))
                Spacer(Modifier.height(4.dp))
                Text(
                    "مسجد ${ArabicText.indic(b.mosque)} • بيت ${ArabicText.indic(b.home)} • لم أصلِّ بعد ${ArabicText.indic(b.notPrayed)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ السنن

private fun sunnahName(id: String, customNames: Map<String, String>): String =
    Sunnah.entries.firstOrNull { it.name == id }?.ar ?: customNames[id] ?: "سنة مخصصة (محذوفة)"

@Composable
private fun RawatibStatsCard(ui: PrayerStatsUi, r: RawatibRangeStats) {
    YaqeenCard(Modifier.fillMaxWidth()) {
        Text("السنن الرواتب", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        if (!r.hasRecords) {
            Text(
                "لا سنن مسجّلة في هذه الفترة. يمكنك تسجيلها من صفحة الصلاة.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@YaqeenCard
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile(r.daysCompleted, "أيام اكتملت فيها السنن الخمس", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            StatTile(r.daysWithAny, "أيام فيها سنن", MaterialTheme.colorScheme.tertiary, Modifier.weight(1f))
            StatTile(r.totalRakaat, "مجموع الركعات", MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        r.perSunnah.forEach { sc ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(sunnahName(sc.sunnahId, ui.customNames), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(
                    "${PrayerFormat.days(sc.days)} • ${PrayerFormat.rakaat(sc.rakaat)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ------------------------------------------------------------------ سجل الأيام

@Composable
private fun HistoryRow(day: PrayerDayStats, today: LocalDate, rawatib: RawatibRangeStats, onClick: () -> Unit) {
    val rakaat = rawatib.perDay.firstOrNull { it.date == day.date }?.rakaat ?: 0
    YaqeenCard(Modifier.fillMaxWidth(), onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(PrayerFormat.dayLabel(day.date, today), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                if (day.recorded == 0) "لا تسجيل" else "سُجّلت ${ArabicText.indic(day.recorded)} من ٥",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Prayer.entries.forEach { p ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StatusDot(day.statuses[p])
                    Spacer(Modifier.height(2.dp))
                    Text(p.ar, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (rakaat > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "السنن: ${PrayerFormat.rakaat(rakaat)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
