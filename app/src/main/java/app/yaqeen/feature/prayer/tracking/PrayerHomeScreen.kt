package app.yaqeen.feature.prayer.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.domain.model.Prayer
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes

/** مسارات صفحات التتبع الإضافية (داخل رسم الصلاة). */
object PrayerRoutes {
    const val SUNNAH = "prayer/sunnah"
}

/** الصفحة الرئيسية لتبويب الصلاة: تسجيل الصلوات الخمس + السنن الرواتب + روابط المواقيت والقبلة والإحصاءات. */
@Composable
fun PrayerHomeScreen(nav: NavHostController, @Suppress("UNUSED_PARAMETER") c: AppContainer) {
    val vm = containerViewModel { PrayerHomeViewModel(it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar(
            title = "الصلاة",
            actions = {
                IconButton(onClick = { nav.navigate(Routes.PRAYER_STATS) }) {
                    Icon(Icons.Filled.BarChart, contentDescription = "إحصاءات الصلاة")
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                ui.loading -> LoadingBox()
                ui.error -> EmptyState(
                    title = "تعذّر تحميل بيانات الصلاة",
                    message = "حدثت مشكلة أثناء القراءة من الجهاز.",
                    action = { TextButton(onClick = vm::retry) { Text("إعادة المحاولة") } },
                    modifier = Modifier.align(Alignment.Center),
                )
                else -> PrayerHomeContent(nav, vm, ui)
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
        }
    }
}

@Composable
private fun PrayerHomeContent(nav: NavHostController, vm: PrayerHomeViewModel, ui: PrayerHomeUi) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "day") { DaySelector(ui, onPrev = vm::previousDay, onNext = vm::nextDay, onToday = vm::goToday) }

        if (ui.isToday) {
            item(key = "next") { NextPrayerCard(ui, onChooseLocation = { nav.navigate(Routes.PRAYER_TIMES) }) }
        }

        item(key = "prayers_title") { SectionTitle("الصلوات الخمس") }
        val highlight = ui.timeline?.let { it.current ?: it.next }
        items(Prayer.entries, key = { it.name }) { prayer ->
            val timeline = ui.timeline
            val isNextToday = timeline != null && timeline.next == prayer && timeline.nextTime?.toLocalDate() == ui.date
            PrayerCard(
                prayer = prayer,
                ui = ui,
                highlighted = highlight == prayer,
                badge = when {
                    timeline?.current == prayer -> "وقتها الآن"
                    isNextToday -> "القادمة"
                    else -> null
                },
                onToggle = { s -> vm.toggle(prayer, s) },
            )
        }
        item(key = "note") {
            Text(
                "التسجيل اختياري؛ وما لم تسجّله لا يُحتسب تقصيرًا. انقر الحالة المختارة مرة أخرى لمسحها.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        item(key = "rawatib_title") { SectionTitle("السنن الرواتب") }
        item(key = "rawatib") { RawatibCard(ui, vm, onManage = { nav.navigate(PrayerRoutes.SUNNAH) }) }

        item(key = "links") {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LinkCard("المواقيت", Icons.Filled.AccessTime, Modifier.weight(1f)) { nav.navigate(Routes.PRAYER_TIMES) }
                LinkCard("القبلة", Icons.Filled.Explore, Modifier.weight(1f)) { nav.navigate(Routes.PRAYER_QIBLA) }
                LinkCard("الإحصاءات", Icons.Filled.BarChart, Modifier.weight(1f)) { nav.navigate(Routes.PRAYER_STATS) }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun DaySelector(ui: PrayerHomeUi, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    YaqeenCard(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                // السهم يتبدّل تلقائيًا في RTL: اليوم السابق على اليمين.
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "اليوم السابق")
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    PrayerFormat.dayLabel(ui.date, ui.today),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
                Text(
                    "${HijriCalendar.dayName(ui.date)} ${HijriCalendar.gregorianLabel(ui.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Text(
                    ui.hijri,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (!ui.isToday) {
                    TextButton(onClick = onToday) { Text("اليوم") }
                }
            }
            IconButton(onClick = onNext, enabled = ui.canGoNext) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "اليوم التالي")
            }
        }
    }
}

@Composable
private fun NextPrayerCard(ui: PrayerHomeUi, onChooseLocation: () -> Unit) {
    val timeline = ui.timeline
    if (ui.times == null || timeline == null) {
        YaqeenCard(Modifier.fillMaxWidth(), onClick = onChooseLocation) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("حدّد موقعك لعرض المواقيت", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "يمكنك تسجيل صلواتك دون موقع، وتظهر المواقيت والوقت المتبقي بعد اختيار المدينة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        return
    }
    val next = timeline.next
    val nextTime = timeline.nextTime
    val minutes = timeline.minutesToNext
    YaqeenCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.primaryContainer) {
        if (next == null || nextTime == null || minutes == null) {
            Text("انتهت صلوات اليوم", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        } else {
            val tomorrow = nextTime.toLocalDate() != ui.date
            Text(
                if (tomorrow) "الصلاة القادمة غدًا" else "الصلاة القادمة",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(next.ar, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(12.dp))
                Text(PrayerFormat.clock12(nextTime), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "يتبقى ${PrayerFormat.remaining(minutes)}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun PrayerCard(
    prayer: Prayer,
    ui: PrayerHomeUi,
    highlighted: Boolean,
    badge: String?,
    onToggle: (app.yaqeen.domain.model.PrayerStatus) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            if (highlighted) 2.dp else 1.dp,
            if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prayer.ar, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                if (badge != null) {
                    Pill(badge)
                    Spacer(Modifier.width(8.dp))
                }
                val time = ui.times?.get(prayer)
                if (time != null) {
                    Text(PrayerFormat.clock12(time), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(10.dp))
            StatusChoiceRow(current = ui.statuses[prayer], enabled = ui.canEdit, onToggle = onToggle)
        }
    }
}

@Composable
private fun RawatibCard(ui: PrayerHomeUi, vm: PrayerHomeViewModel, onManage: () -> Unit) {
    val day = ui.checklist
    YaqeenCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "أنجزتَ ${ArabicText.indic(day.doneCount)} من ${ArabicText.indic(day.totalCount)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (ui.isToday) "إجمالي ركعات السنن اليوم: ${PrayerFormat.rakaat(day.totalRakaat)}"
                    else "إجمالي ركعات السنن في هذا اليوم: ${PrayerFormat.rakaat(day.totalRakaat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(if (day.totalCount == 0) 0f else day.doneCount.toFloat() / day.totalCount)
        Spacer(Modifier.height(4.dp))
        RawatibChecklist(day = day, enabled = ui.canEdit, onToggle = { item, done -> vm.setSunnah(item, done) })
        TextButton(onClick = onManage, modifier = Modifier.align(Alignment.End)) { Text("إضافة سنة أو إدارة السنن") }
    }
}

@Composable
private fun LinkCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    YaqeenCard(modifier, onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
