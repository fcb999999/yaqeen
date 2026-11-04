package app.yaqeen.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.feature.calendar.IslamicEvent
import app.yaqeen.feature.journey.ArabicNoun
import app.yaqeen.feature.journey.JourneyFormat
import app.yaqeen.feature.prayer.times.NextPrayer
import app.yaqeen.feature.prayer.tracking.PrayerQuickRow
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.ProgressRing
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTheme
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.ZonedDateTime

/** الرئيسية: لوحة شخصية حيّة (كلها Flow من قاعدة البيانات المحلية فتعمل بلا إنترنت). */
@Composable
fun HomeScreen(nav: NavHostController, c: AppContainer) {
    val vm = containerViewModel { HomeViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    var showCustomize by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar(
            "الرئيسية",
            actions = {
                IconButton(onClick = { showCustomize = true }) { Icon(Icons.Filled.Tune, contentDescription = "تخصيص") }
            },
        )
        when (val s = state) {
            HomeUiState.Loading -> LoadingBox()
            is HomeUiState.Error -> EmptyState(
                title = s.message,
                modifier = Modifier.fillMaxWidth(),
                action = { Button(onClick = vm::reload) { Text("إعادة المحاولة") } },
            )
            is HomeUiState.Ready -> {
                HomeContent(s.data, vm, nav, c)
                if (showCustomize) {
                    CustomizeSheet(
                        csv = s.data.sectionsCsv,
                        onChange = vm::saveSections,
                        onDismiss = { showCustomize = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeContent(data: HomeData, vm: HomeViewModel, nav: NavHostController, c: AppContainer) {
    val visible = remember(data.sectionsCsv) { HomeSections.visible(data.sectionsCsv) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") { HeaderCard(data) }
        if (visible.isEmpty()) {
            item(key = "none") {
                EmptyState(
                    title = "لا أقسام معروضة",
                    message = "أظهر ما تحب من زر «تخصيص» في أعلى الصفحة.",
                )
            }
        }
        items(visible, key = { it }) { key ->
            when (key) {
                HomeSections.OVERALL -> OverallCard(data)
                HomeSections.NEXT_PRAYER -> NextPrayerCard(vm, nav)
                HomeSections.PRAYERS -> PrayersCard(data, nav, c)
                HomeSections.WIRD -> WirdCard(data, nav)
                HomeSections.CONTINUE_READING -> ContinueReadingCard(data, nav)
                HomeSections.ADHKAR -> AdhkarCard(data, nav)
                HomeSections.RAWATIB -> RawatibCard(data, nav)
                HomeSections.TASKS -> TasksCard(data, nav)
                HomeSections.EVENT -> EventCard(data, nav)
            }
        }
    }
}

// ------------------------------------------------------------ الترويسة

@Composable
private fun HeaderCard(data: HomeData) {
    val hour = remember(data.date) { ZonedDateTime.now().hour }
    YaqeenCard(containerColor = MaterialTheme.colorScheme.primaryContainer) {
        Text("السلام عليكم", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(HomeText.greeting(hour), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(8.dp))
        Text(
            "${HijriCalendar.dayName(data.date)} · ${data.hijri.format()}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            HijriCalendar.gregorianLabel(data.date),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

// ------------------------------------------------------------ بطاقة عامة بعنوان

@Composable
private fun HomeCard(title: String, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    YaqeenCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
            }
        }
        Spacer(Modifier.height(8.dp))
        content()
    }
}

// ------------------------------------------------------------ إنجاز اليوم

@Composable
private fun OverallCard(data: HomeData) {
    val o = data.overall
    val seed = data.date.toEpochDay()
    HomeCard(HomeSections.title(HomeSections.OVERALL)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(o.ratio, size = 84.dp, stroke = 9.dp, label = JourneyFormat.percent(o.ratio.toDouble()))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "أتممتَ ${ArabicText.indic(o.done)} من ${ArabicText.indic(o.total)} مما هو متاح لك اليوم",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(4.dp))
                Text(HomeText.motivation(o.ratio, seed), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------ الصلاة القادمة

/** ساعة تتحرك كل ثانية فقط والتطبيق ظاهر (STARTED)، فلا تستهلك البطارية في الخلفية. */
@Composable
private fun rememberNowMillis(): State<Long> {
    val owner = LocalLifecycleOwner.current
    return produceState(System.currentTimeMillis(), owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                val now = System.currentTimeMillis()
                value = now
                delay(1_000 - now % 1_000)
            }
        }
    }
}

@Composable
private fun NextPrayerCard(vm: HomeViewModel, nav: NavHostController) {
    val st by vm.nextPrayer.collectAsStateWithLifecycle()
    when (val s = st) {
        NextPrayerState.Loading -> HomeCard(HomeSections.title(HomeSections.NEXT_PRAYER)) {
            Text("جارٍ حساب المواقيت…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NextPrayerState.NoLocation -> HomeCard(HomeSections.title(HomeSections.NEXT_PRAYER), onClick = { nav.navigate(Routes.PRAYER_TIMES) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("حدّد موقعك لعرض مواقيت الصلاة", style = MaterialTheme.typography.bodyLarge)
                    Text("اضغط هنا لاختيار المدينة أو استخدام موقعك الحالي.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        is NextPrayerState.Ready -> NextPrayerReady(s.next, nav)
    }
}

@Composable
private fun NextPrayerReady(next: NextPrayer, nav: NavHostController) {
    val nowMs by rememberNowMillis()
    val remaining = Duration.between(java.time.Instant.ofEpochMilli(nowMs), next.time.toInstant())
    val spoken = HomeText.spokenRemaining(remaining)
    HomeCard(HomeSections.title(HomeSections.NEXT_PRAYER), onClick = { nav.navigate(Routes.PRAYER_TIMES) }) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "${next.prayer.ar} $spoken" }) {
            Column(Modifier.weight(1f)) {
                Text(next.prayer.ar, style = MaterialTheme.typography.headlineSmall)
                Text(HomeText.clock12(next.time), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (remaining.isNegative || remaining.isZero) "حان الوقت" else HomeText.countdown(remaining),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text("المتبقي", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ------------------------------------------------------------ الصلوات

@Composable
private fun PrayersCard(data: HomeData, nav: NavHostController, c: AppContainer) {
    val prayed = PrayersToday.prayed(data.prayers)
    HomeCard(HomeSections.title(HomeSections.PRAYERS), onClick = { nav.navigate(Routes.PRAYER) }) {
        // الصف نفسه الذي تستعمله ميزة الصلاة: الضغط على صلاة يفتح قائمة (مسجد/بيت/لم أصلِّ بعد/مسح).
        PrayerQuickRow(c, data.date)
        Spacer(Modifier.height(8.dp))
        val recorded = data.prayers.count { it.status != null }
        Text(
            if (recorded == 0) "لم تسجّل صلوات اليوم بعد. المس صلاةً لتسجيلها."
            else "أدّيتَ ${ArabicText.indic(prayed)} من ٥ صلوات مسجّلة اليوم" + if (recorded < 5) " (والبقية غير مسجّلة)" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ------------------------------------------------------------ الورد

@Composable
private fun WirdCard(data: HomeData, nav: NavHostController) {
    if (data.wird.isEmpty()) {
        HomeCard(HomeSections.title(HomeSections.WIRD), onClick = { nav.navigate(Routes.quranPlans()) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Add, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("لا ورد نشط. أنشئ وردًا يناسبك لتتابعه هنا.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        return
    }
    HomeCard(HomeSections.title(HomeSections.WIRD)) {
        data.wird.forEachIndexed { i, w ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Column(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.quranPlans(w.plan.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(w.plan.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(
                        if (w.targetMet) "اكتمل ورد اليوم" else "اليوم: ${w.todayLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (w.targetMet) YaqeenTheme.extra.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(6.dp))
                ProgressBar(w.ratio)
                w.remainingPages?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (it == 0) "أتممتَ هذه الخطة" else "المتبقي من الخطة: ${ArabicNoun.PAGE.count(it) { n -> ArabicText.indic(n) }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ أكمل القراءة

@Composable
private fun ContinueReadingCard(data: HomeData, nav: NavHostController) {
    val r = data.reading
    if (r == null) {
        HomeCard(HomeSections.title(HomeSections.CONTINUE_READING), onClick = { nav.navigate(Routes.quranReader()) }) {
            Text("لم تبدأ القراءة بعد. اضغط لفتح المصحف.", style = MaterialTheme.typography.bodyMedium)
        }
    } else {
        HomeCard(HomeSections.title(HomeSections.CONTINUE_READING), onClick = { nav.navigate(Routes.quranReader(r.surah, r.ayah)) }) {
            val name = r.surahName?.let { "سورة $it" } ?: "السورة ${ArabicText.indic(r.surah)}"
            Text("$name · الآية ${ArabicText.indic(r.ayah)}", style = MaterialTheme.typography.titleMedium)
            Text("الصفحة ${ArabicText.indic(r.page)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ------------------------------------------------------------ الأذكار

@Composable
private fun AdhkarCard(data: HomeData, nav: NavHostController) {
    HomeCard(HomeSections.title(HomeSections.ADHKAR)) {
        val rows = data.adhkar
        if (rows == null) {
            Text("جارٍ التحميل…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeCard
        }
        rows.forEachIndexed { i, r ->
            if (i > 0) Spacer(Modifier.height(10.dp))
            Column(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.adhkarCategory(r.categoryId)) }.padding(vertical = 2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(r.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text(
                        when {
                            r.complete -> "تم"
                            r.started -> "${ArabicText.indic(r.done)} من ${ArabicText.indic(r.total)}"
                            else -> "غير مسجّل"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (r.complete) YaqeenTheme.extra.success else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                ProgressBar(r.ratio)
            }
        }
    }
}

// ------------------------------------------------------------ السنن

@Composable
private fun RawatibCard(data: HomeData, nav: NavHostController) {
    val r = data.rawatib
    HomeCard(HomeSections.title(HomeSections.RAWATIB), onClick = { nav.navigate(Routes.PRAYER) }) {
        if (r.doneDefaults == 0 && r.customDone == 0) {
            Text("لم تسجّل سننًا اليوم.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                "${ArabicText.indic(r.doneDefaults)} من ${ArabicText.indic(r.totalDefaults)} · ${ArabicNoun.RAKAA.count(r.rakaat) { n -> ArabicText.indic(n) }}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        Spacer(Modifier.height(6.dp))
        ProgressBar(r.doneDefaults.toFloat() / r.totalDefaults)
    }
}

// ------------------------------------------------------------ المهام

@Composable
private fun TasksCard(data: HomeData, nav: NavHostController) {
    val p = data.taskProgress
    HomeCard(HomeSections.title(HomeSections.TASKS), onClick = { nav.navigate(Routes.TASKS) }) {
        if (p.total == 0) {
            Text("لا مهام لليوم. أضف مهامك من صفحة المهام.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@HomeCard
        }
        Text("أنجزتَ ${ArabicText.indic(p.done)} من ${ArabicText.indic(p.total)}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        ProgressBar(p.ratio)
        val pending = data.tasks.filter { !it.done }.take(3)
        if (pending.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            pending.forEach {
                Text("• ${it.task.title}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val more = data.tasks.count { !it.done } - pending.size
            if (more > 0) Text("و${ArabicText.indic(more)} أخرى", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        } else {
            Spacer(Modifier.height(6.dp))
            Text("أتممتَ مهام اليوم كلها.", style = MaterialTheme.typography.bodySmall, color = YaqeenTheme.extra.success)
        }
    }
}

// ------------------------------------------------------------ المناسبة

@Composable
private fun EventCard(data: HomeData, nav: NavHostController) {
    val e: IslamicEvent = data.event ?: return
    HomeCard(HomeSections.title(HomeSections.EVENT), onClick = { nav.navigate(Routes.CALENDAR) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(e.nameAr, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${e.hijri.format()} · ${HijriCalendar.gregorianLabel(e.date)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(HomeText.daysUntil(e.daysUntil), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

// ------------------------------------------------------------ التخصيص

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomizeSheet(csv: String, onChange: (String) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val layout = HomeSections.layout(csv)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text("تخصيص الرئيسية", style = MaterialTheme.typography.titleLarge)
            Text(
                "أظهر الأقسام التي تهمّك وأخفِ غيرها، ورتّبها كما تحب.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            val lastVisible = layout.indexOfLast { it.visible }
            layout.forEachIndexed { i, entry ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = entry.visible, onCheckedChange = { onChange(HomeSections.toggle(csv, entry.key)) })
                    Spacer(Modifier.width(12.dp))
                    Text(
                        HomeSections.title(entry.key),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (entry.visible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { onChange(HomeSections.move(csv, entry.key, -1)) }, enabled = entry.visible && i > 0) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "تحريك ${HomeSections.title(entry.key)} للأعلى", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = { onChange(HomeSections.move(csv, entry.key, 1)) }, enabled = entry.visible && i < lastVisible) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "تحريك ${HomeSections.title(entry.key)} للأسفل", modifier = Modifier.size(20.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onChange("") }, modifier = Modifier.weight(1f)) { Text("الإعداد الافتراضي") }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("تم") }
            }
        }
    }
}
