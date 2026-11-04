package app.yaqeen.feature.prayer.times

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.Prayer
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.containerViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId

class PrayerCalcSettingsViewModel(private val c: AppContainer) : ViewModel() {
    val calc: StateFlow<CalcSettings> = c.prayerTimes.observeCalcSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalcSettings())
    val location: StateFlow<ResolvedLocation?> = c.prayerTimes.observeLocation()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val hijriAdjust: StateFlow<Int> = c.settings.observe(Settings.HIJRI_ADJUST_DAYS)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** طريقة مقترحة لبلد المدينة المختارة يدويًا (إن عرفناها). */
    val suggestion: StateFlow<String?> = c.prayerTimes.observeLocation().map { loc ->
        if (loc == null || !loc.isManual) null
        else CityRepository.load(c.context).firstOrNull { it.name == loc.name }?.let { CalcMethods.suggestedForCountry(it.country) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun times(loc: ResolvedLocation, calc: CalcSettings, date: java.time.LocalDate = java.time.LocalDate.now(ZoneId.of(loc.zoneId))): PrayerTimesDay? =
        c.prayerTimes.timesFor(date, loc, calc)

    fun setMethod(id: String) = save { c.settings.set(Settings.PRAYER_METHOD, id) }
    fun setMadhab(id: String) = save { c.settings.set(Settings.PRAYER_MADHAB, id) }
    fun setHighLat(id: String) = save { c.settings.set(Settings.PRAYER_HIGH_LAT_RULE, id) }
    fun setAdjustments(a: MinuteAdjustments) = save { c.settings.set(PrayerKeys.ADJUSTMENTS, a.encode()) }
    fun setHijriAdjust(days: Int) = save { c.settings.set(Settings.HIJRI_ADJUST_DAYS, days.coerceIn(-2, 2)) }

    private fun save(block: suspend () -> Unit) {
        viewModelScope.launch {
            block()
            c.requestReschedule()
        }
    }
}

@Composable
fun PrayerCalcSettingsScreen(nav: NavHostController) {
    val vm = containerViewModel { PrayerCalcSettingsViewModel(it) }
    val calc by vm.calc.collectAsStateWithLifecycle()
    val loc by vm.location.collectAsStateWithLifecycle()
    val hijri by vm.hijriAdjust.collectAsStateWithLifecycle()
    val suggestion by vm.suggestion.collectAsStateWithLifecycle()

    PrayerScreenFrame(title = "إعدادات حساب المواقيت", onBack = { nav.popBackStack() }) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val l = loc
            if (l != null) PreviewCard(vm.times(l, calc), l)

            if (suggestion != null && suggestion != calc.method) {
                YaqeenCard(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                    Text(
                        "الطريقة الشائعة في بلد ${l?.name.orEmpty()}: ${CalcMethods.nameOf(suggestion!!)}. يمكنك اعتمادها إن وافقت تقويم مسجدك.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    TextButton(onClick = { vm.setMethod(suggestion!!) }) { Text("اعتماد هذه الطريقة") }
                }
            }

            SectionTitle("طريقة الحساب")
            YaqeenCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 8.dp)) {
                CalcMethods.ALL.forEach { m ->
                    OptionRow(m.nameAr, m.descAr, selected = calc.method == m.id) { vm.setMethod(m.id) }
                }
            }

            SectionTitle("المذهب في وقت العصر")
            YaqeenCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 8.dp)) {
                CalcMethods.MADHABS.forEach { (id, label) -> OptionRow(label, null, selected = calc.madhab == id) { vm.setMadhab(id) } }
            }

            SectionTitle("المناطق العالية العرض")
            YaqeenCard(contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 8.dp)) {
                Text(
                    "تُستخدم عندما لا يغيب الشفق أو لا تتحدد زاوية الفجر والعشاء في الصيف الأبيض (بلدان الشمال خصوصًا).",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
                CalcMethods.HIGH_LAT_RULES.forEach { (id, label) -> OptionRow(label, null, selected = calc.highLatRule == id) { vm.setHighLat(id) } }
            }

            SectionTitle("تعديل الدقائق")
            YaqeenCard {
                Text(
                    "للتوفيق مع تقويم مسجدك أو بلدك: أضف أو اطرح دقائق من وقت كل صلاة.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Prayer.entries.forEach { p ->
                    StepperRow(
                        label = p.ar, value = PrayerFormat.offset(calc.adjustments[p]),
                        canDec = calc.adjustments[p] > MinuteAdjustments.RANGE.first, canInc = calc.adjustments[p] < MinuteAdjustments.RANGE.last,
                        onDec = { vm.setAdjustments(calc.adjustments.with(p, calc.adjustments[p] - 1)) },
                        onInc = { vm.setAdjustments(calc.adjustments.with(p, calc.adjustments[p] + 1)) },
                    )
                }
                if (!calc.adjustments.isZero) TextButton(onClick = { vm.setAdjustments(MinuteAdjustments()) }) { Text("إلغاء كل التعديلات") }
            }

            SectionTitle("التاريخ الهجري")
            YaqeenCard {
                Text(
                    "لاختلاف رؤية الهلال بين البلدان يمكنك تقديم التاريخ الهجري أو تأخيره يومًا أو يومين.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                StepperRow(
                    label = "التعديل", value = if (hijri == 0) "بدون تعديل" else if (hijri > 0) "+${ArabicText.indic(hijri)} يوم" else "−${ArabicText.indic(-hijri)} يوم",
                    canDec = hijri > -2, canInc = hijri < 2,
                    onDec = { vm.setHijriAdjust(hijri - 1) }, onInc = { vm.setHijriAdjust(hijri + 1) },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PreviewCard(day: PrayerTimesDay?, loc: ResolvedLocation) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val is24h = remember { android.text.format.DateFormat.is24HourFormat(context) }
    YaqeenCard {
        Text("معاينة مواقيت اليوم · ${loc.name}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        if (day == null) {
            Text("تعذّر الحساب بهذه الإعدادات.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Prayer.entries.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(p.ar, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(PrayerFormat.clock(day.timeOf(p), is24h), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onClick, role = Role.RadioButton).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun StepperRow(label: String, value: String, canDec: Boolean, canInc: Boolean, onDec: () -> Unit, onInc: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onDec, enabled = canDec) { Icon(Icons.Filled.Remove, "إنقاص") }
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(88.dp), textAlign = TextAlign.Center)
        IconButton(onClick = onInc, enabled = canInc) { Icon(Icons.Filled.Add, "زيادة") }
    }
}
