package app.yaqeen.feature.tasks

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.data.settings.Settings
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface SummaryUi {
    data object Loading : SummaryUi
    data object Failed : SummaryUi
    data class Ready(val summary: DaySummary, val hijri: String) : SummaryUi
}

class DaySummaryVm(private val c: AppContainer) : ViewModel() {
    private val date = MutableStateFlow<LocalDate?>(null)

    fun setDate(d: LocalDate) { date.value = d }

    @OptIn(ExperimentalCoroutinesApi::class)
    val ui: StateFlow<SummaryUi> = date.filterNotNull().flatMapLatest { d ->
        combine(
            c.taskEngine.observeDay(d),
            observeWorshipDay(c, d),
            flow { emit(runCatching { c.adhkar() }.getOrNull()) },
            c.settings.observe(Settings.HIJRI_ADJUST_DAYS),
        ) { tasks, worship, catalog, adjust ->
            val cats = catalog?.categories.orEmpty()
                .filter { it.id in DayEndSummary.DAILY_ADHKAR }
                .map { cat -> AdhkarCategoryInfo(cat.id, cat.title, cat.items.map { it.id }) }
            SummaryUi.Ready(DayEndSummary.build(d, tasks, worship, cats), HijriCalendar.fromGregorian(d, adjust).format()) as SummaryUi
        }.catch { emit(SummaryUi.Failed) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUi.Loading)

    val dayEndEnabled: StateFlow<Boolean> =
        c.settings.observe(Settings.NOTIF_DAY_END).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val dayEndTime: StateFlow<String> =
        c.settings.observe(Settings.NOTIF_DAY_END_TIME).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "23:00")

    fun setEnabled(on: Boolean) {
        viewModelScope.launch {
            c.settings.set(Settings.NOTIF_DAY_END, on)
            c.requestReschedule()
        }
    }

    fun setTime(hhmm: String) {
        viewModelScope.launch {
            c.settings.set(Settings.NOTIF_DAY_END_TIME, hhmm)
            c.requestReschedule()
        }
    }
}

@Composable
fun DaySummaryScreen(nav: NavHostController, date: LocalDate) {
    val vm = containerViewModel(key = "day-summary-$date") { DaySummaryVm(it) }
    LaunchedEffect(date) { vm.setDate(date) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val enabled by vm.dayEndEnabled.collectAsStateWithLifecycle()
    val time by vm.dayEndTime.collectAsStateWithLifecycle()
    var showTime by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("ملخص اليوم", onBack = { nav.popBackStack() })
        when (val s = ui) {
            SummaryUi.Loading -> LoadingBox()
            SummaryUi.Failed -> EmptyState("تعذّر تحميل الملخص", "حاول مرة أخرى بعد قليل.", Icons.Filled.Checklist)
            is SummaryUi.Ready -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SummaryBody(nav, s)
                YaqeenCard(Modifier.fillMaxWidth()) {
                    SectionTitle("تنبيه نهاية اليوم")
                    SwitchRow(
                        title = "تنبيه بملخص اليوم",
                        subtitle = "تنبيه هادئ يفتح هذا الملخص ويعرض ما سُجِّل وما لم يُسجَّل",
                        checked = enabled,
                        onCheckedChange = { vm.setEnabled(it) },
                    )
                    if (enabled) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("الوقت: " + (ArabicFormat.time12(time) ?: time), modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { showTime = true }) { Text("تغيير") }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (showTime) TimePickerDialogAr(time, onDismiss = { showTime = false }, onPick = { vm.setTime(it) })
}

@Composable
private fun SummaryBody(nav: NavHostController, s: SummaryUi.Ready) {
    val sum = s.summary
    YaqeenCard(Modifier.fillMaxWidth()) {
        Text(HijriCalendar.dayName(sum.date) + "، " + HijriCalendar.gregorianLabel(sum.date), style = MaterialTheme.typography.titleMedium)
        Text(s.hijri, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(
            if (sum.allClear) "ما شاء الله، لا شيء متبقٍّ لهذا اليوم."
            else "هذا ما سُجِّل في يومك. ما لم يُسجَّل لا يعني أنه لم يحدث، وبإمكانك تسجيله الآن.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    // المهام
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("المهام")
        if (sum.tasksTotal == 0) {
            Text("لا مهام لهذا اليوم.", style = MaterialTheme.typography.bodyMedium)
        } else {
            Text("المنجزة (${ArabicText.indic(sum.tasksDone.size)})", style = MaterialTheme.typography.labelLarge)
            if (sum.tasksDone.isEmpty()) Muted("لا شيء بعد")
            sum.tasksDone.forEach { StatusLine(it.task.title, true) }
            Spacer(Modifier.height(8.dp))
            Text("غير المنجزة (${ArabicText.indic(sum.tasksPending.size)})", style = MaterialTheme.typography.labelLarge)
            if (sum.tasksPending.isEmpty()) Muted("أنجزتَ كل المهام")
            sum.tasksPending.forEach { StatusLine(it.task.title, false) }
        }
        TextButton(onClick = { nav.navigate(TasksRoutes.day(sum.date)) }) { Text("فتح مهام اليوم") }
    }

    // الصلوات
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("الصلوات")
        Text("المسجّلة: ${ArabicText.indic(sum.prayersRecorded)} من ${ArabicText.indic(5)}", style = MaterialTheme.typography.bodyMedium)
        if (sum.prayersUnrecorded.isEmpty()) Muted("كل الصلوات مسجّلة")
        else Muted("غير مسجّلة: " + sum.prayersUnrecorded.joinToString("، ") { it.ar })
        TextButton(onClick = { runCatching { nav.navigate(Routes.PRAYER) } }) { Text("فتح الصلاة") }
    }

    // السنن
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("السنن الرواتب")
        Text("المسجّلة: ${ArabicText.indic(sum.rawatibRecorded)} من ${ArabicText.indic(5)}", style = MaterialTheme.typography.bodyMedium)
        if (sum.rawatibUnrecorded.isEmpty()) Muted("كل السنن الرواتب مسجّلة")
        else Muted("غير مسجّلة: " + sum.rawatibUnrecorded.joinToString("، ") { it.ar })
        TextButton(onClick = { runCatching { nav.navigate(Routes.PRAYER) } }) { Text("فتح الصلاة والسنن") }
    }

    // الأذكار
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("الأذكار")
        if (sum.adhkarIncomplete.isEmpty()) {
            Muted("أذكار الصباح والمساء والنوم مكتملة")
        } else {
            sum.adhkarIncomplete.forEach { g ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "${g.title}: ${ArabicText.indic(g.done)} من ${ArabicText.indic(g.total)}",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { runCatching { nav.navigate(Routes.adhkarCategory(g.categoryId)) } }) { Text("فتح") }
                }
            }
        }
    }

    // الورد
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("الورد القرآني")
        if (sum.wird.isEmpty()) {
            Muted("لا ورد نشط. يمكنك إنشاء ورد من قسم القرآن.")
        } else {
            sum.wird.forEach { w ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(w.planName, style = MaterialTheme.typography.bodyMedium)
                        Muted(w.remainingText ?: "تم بلوغ المقدار اليومي")
                    }
                    if (!w.met) TextButton(onClick = {
                        runCatching { nav.navigate(Routes.quranPlans(w.planId)) }
                    }) { Text("فتح") }
                }
            }
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StatusLine(title: String, done: Boolean) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (done) YaqeenTheme.extra.success else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium)
    }
}
