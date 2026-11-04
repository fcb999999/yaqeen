package app.yaqeen.feature.prayer.times

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.Prayer
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

sealed interface LocState {
    data object Loading : LocState
    data object None : LocState
    data class Ready(val loc: ResolvedLocation) : LocState
}

class PrayerTimesViewModel(val c: AppContainer) : ViewModel() {
    val location: StateFlow<LocState> = c.prayerTimes.observeLocation()
        .map { if (it == null) LocState.None else LocState.Ready(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LocState.Loading)

    val calc: StateFlow<CalcSettings> = c.prayerTimes.observeCalcSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalcSettings())

    val hijriAdjust: StateFlow<Int> = c.settings.observe(Settings.HIJRI_ADJUST_DAYS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun times(date: java.time.LocalDate, loc: ResolvedLocation, calc: CalcSettings): PrayerTimesDay? =
        c.prayerTimes.timesFor(date, loc, calc)
}

private val PRAYER_ROWS: List<Pair<Prayer?, String>> = listOf(
    Prayer.FAJR to "الفجر", null to "الشروق", Prayer.DHUHR to "الظهر",
    Prayer.ASR to "العصر", Prayer.MAGHRIB to "المغرب", Prayer.ISHA to "العشاء",
)

@Composable
fun PrayerTimesScreen(nav: NavHostController) {
    val vm = containerViewModel { PrayerTimesViewModel(it) }
    val locState by vm.location.collectAsStateWithLifecycle()
    val calc by vm.calc.collectAsStateWithLifecycle()
    val hijriAdjust by vm.hijriAdjust.collectAsStateWithLifecycle()
    var dayOffset by rememberSaveable { mutableIntStateOf(0) }

    PrayerScreenFrame(
        title = "مواقيت الصلاة",
        onBack = { nav.popBackStack() },
        actions = {
            IconButton(onClick = { nav.navigate(PrayerRoutes.ADHAN_SETTINGS) }) { Icon(Icons.Filled.NotificationsActive, "إعدادات الأذان والتنبيهات") }
            IconButton(onClick = { nav.navigate(PrayerRoutes.CALC_SETTINGS) }) { Icon(Icons.Filled.Tune, "إعدادات الحساب") }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (val st = locState) {
                LocState.Loading -> LoadingBox()
                LocState.None -> NoLocation(
                    onChooseLocation = { nav.navigate(PrayerRoutes.LOCATION) },
                )
                is LocState.Ready -> TimesContent(
                    vm = vm, nav = nav, loc = st.loc, calc = calc, hijriAdjust = hijriAdjust,
                    dayOffset = dayOffset, onDayOffset = { dayOffset = it },
                )
            }
        }
    }
}

@Composable
private fun NoLocation(onChooseLocation: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
        EmptyState(
            title = "حدّد موقعك لعرض المواقيت",
            message = "تُحسب مواقيت الصلاة وفق موقعك على جهازك دون إنترنت. يمكنك تحديده تلقائيًا بإذنك أو اختيار مدينتك يدويًا.",
            icon = Icons.Filled.LocationOn,
            action = { Button(onClick = onChooseLocation) { Text("تحديد الموقع") } },
        )
    }
}

@Composable
private fun TimesContent(
    vm: PrayerTimesViewModel,
    nav: NavHostController,
    loc: ResolvedLocation,
    calc: CalcSettings,
    hijriAdjust: Int,
    dayOffset: Int,
    onDayOffset: (Int) -> Unit,
) {
    val context = LocalContext.current
    val is24h = remember { android.text.format.DateFormat.is24HourFormat(context) }
    val zone = remember(loc.zoneId) { ZoneId.of(loc.zoneId) }
    val nowMillis by rememberNowMillis(1000L)
    val nowZ = Instant.ofEpochMilli(nowMillis).atZone(zone)
    val today = nowZ.toLocalDate()
    val selectedDate = today.plusDays(dayOffset.toLong())

    val todayTimes = remember(loc, calc, today) { vm.times(today, loc, calc) }
    val tomorrowTimes = remember(loc, calc, today) { vm.times(today.plusDays(1), loc, calc) }
    val dayTimes = remember(loc, calc, selectedDate) { vm.times(selectedDate, loc, calc) }
    val next: NextPrayer? = if (todayTimes != null && tomorrowTimes != null) PrayerCalculator.nextAfter(nowZ, todayTimes, tomorrowTimes) else null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LocationRow(loc = loc, onClick = { nav.navigate(PrayerRoutes.LOCATION) }, onQibla = { nav.navigate(app.yaqeen.ui.navigation.Routes.PRAYER_QIBLA) })
        DateCard(
            date = selectedDate, isToday = dayOffset == 0, hijriAdjust = hijriAdjust,
            onPrev = { onDayOffset(dayOffset - 1) }, onNext = { onDayOffset(dayOffset + 1) }, onToday = { onDayOffset(0) },
        )
        if (next != null) NextPrayerCard(next = next, nowZ = nowZ, is24h = is24h)
        if (dayTimes == null) {
            YaqeenCard {
                Text("تعذّر حساب المواقيت لهذا الموقع في هذا التاريخ.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("جرّب تغيير قاعدة خطوط العرض العالية من إعدادات الحساب أو اختر مدينة أخرى.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            TimesList(day = dayTimes, next = next, nowZ = nowZ, isToday = dayOffset == 0, is24h = is24h)
        }
        Text(
            "طريقة الحساب: ${CalcMethods.nameOf(calc.method)} – المذهب في العصر: ${CalcMethods.madhabName(calc.madhab)}" +
                if (calc.adjustments.isZero) "" else " – مع تعديل يدوي",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        TextButton(onClick = { nav.navigate(PrayerRoutes.CALC_SETTINGS) }, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("تغيير طريقة الحساب") }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LocationRow(loc: ResolvedLocation, onClick: () -> Unit, onQibla: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        AssistChip(
            onClick = onClick,
            label = { Text(loc.name) },
            leadingIcon = { Icon(Icons.Filled.LocationOn, null, Modifier.size(18.dp)) },
        )
        Text(
            if (loc.isManual) "مدينة مختارة" else "حسب موقعك",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = onQibla) {
            Icon(Icons.Filled.Explore, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("القبلة")
        }
    }
}

@Composable
private fun DateCard(date: java.time.LocalDate, isToday: Boolean, hijriAdjust: Int, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    val hijri = HijriCalendar.fromGregorian(date, hijriAdjust)
    YaqeenCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // في اتجاه RTL يظهر أول عنصر على اليمين: اليوم السابق يمينًا والتالي يسارًا.
            IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronRight, "اليوم السابق") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (isToday) "اليوم – ${HijriCalendar.dayName(date)}" else HijriCalendar.dayName(date),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(hijri.format(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Text(HijriCalendar.gregorianLabel(date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!isToday) TextButton(onClick = onToday) { Text("العودة إلى اليوم") }
            }
            IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronLeft, "اليوم التالي") }
        }
    }
}

@Composable
private fun NextPrayerCard(next: NextPrayer, nowZ: ZonedDateTime, is24h: Boolean) {
    val remaining = Duration.between(nowZ, next.time).seconds
    YaqeenCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("الصلاة القادمة", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                "${next.prayer.ar}  –  ${PrayerFormat.clock(next.time, is24h)}",
                style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                PrayerFormat.countdown(remaining),
                fontSize = 40.sp, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.displaySmall,
            )
            Text("متبقٍّ على الأذان", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

@Composable
private fun TimesList(day: PrayerTimesDay, next: NextPrayer?, nowZ: ZonedDateTime, isToday: Boolean, is24h: Boolean) {
    val isFriday = day.date.dayOfWeek == java.time.DayOfWeek.FRIDAY
    YaqeenCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
        PRAYER_ROWS.forEach { (prayer, label) ->
            val time = if (prayer == null) day.sunrise else day.timeOf(prayer)
            val isNext = prayer != null && next != null && next.prayer == prayer && next.time.toInstant() == time.toInstant()
            val isPast = isToday && !time.isAfter(nowZ)
            val rowLabel = if (prayer == Prayer.DHUHR && isFriday) "الظهر (الجمعة)" else label
            TimeRow(label = rowLabel, time = PrayerFormat.clock(time, is24h), highlighted = isNext, dim = isPast && !isNext, minor = prayer == null)
        }
    }
}

@Composable
private fun TimeRow(label: String, time: String, highlighted: Boolean, dim: Boolean, minor: Boolean) {
    val bg = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent
    val textColor = when {
        highlighted -> MaterialTheme.colorScheme.onSecondaryContainer
        dim || minor -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).background(bg, MaterialTheme.shapes.small).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (minor) Icon(Icons.Filled.WbSunny, null, Modifier.size(18.dp), tint = YaqeenTheme.extra.gold) else Spacer(Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, style = if (minor) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, color = textColor, modifier = Modifier.weight(1f))
        Text(time, style = if (minor) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium, color = textColor, fontWeight = if (highlighted) FontWeight.Bold else null)
    }
}
