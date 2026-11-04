package app.yaqeen.feature.journey

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.AppContainer
import app.yaqeen.feature.journey.JourneyFormat.n
import app.yaqeen.feature.journey.JourneyFormat.percent
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.ProgressBar
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.theme.YaqeenTheme

/** "رحلتي مع يقين": إحصاءات شخصية هادئة. لا ترتيب ولا منافسة ولا نقاط ولا مشاركة مع أحد. */
@Composable
fun JourneyScreen(nav: NavHostController, c: AppContainer) {
    val vm = containerViewModel { JourneyViewModel(it) }
    val state by vm.state.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("رحلتي مع يقين", onBack = { nav.popBackStack() })
        when (val s = state) {
            JourneyUiState.Loading -> LoadingBox()
            is JourneyUiState.Ready -> JourneyContent(s, period, vm::setPeriod, vm::retry)
        }
    }
}

@Composable
private fun JourneyContent(s: JourneyUiState.Ready, period: Int, onPeriod: (Int) -> Unit, onRetry: () -> Unit) {
    val d = s.data
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { IntroNote() }
        if (s.updateFailed) item { UpdateFailedNote(onRetry) }
        if (!d.hasAnyData) {
            item {
                YaqeenCard {
                    EmptyState(
                        title = "رحلتك تبدأ بأول تسجيل",
                        message = "حين تسجّل صلاة أو ذكرًا أو قراءة أو سنة أو مهمة سترى هنا صورة هادئة لمسيرتك. لا شيء يُحسب عليك قبل أن تسجّله.",
                        icon = Icons.Filled.Timeline,
                    )
                }
            }
            return@LazyColumn
        }
        item { PeriodSelector(period, onPeriod) }
        item { EncouragementCard(d.encouragement, d.period) }
        item { StreakCard(d) }
        item { HeatmapCard(d) }
        item { ChartsCard(d) }
        item { ComparisonCard(d, period) }
        item { QuranCard(d) }
        item { AdhkarCard(d) }
        item { PrayersCard(d) }
        item { RawatibCard(d) }
        item { TasksCard(d) }
        item { PrivacyNote() }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ------------------------------------------------------------ مكونات عامة

@Composable
private fun IntroNote() {
    Text(
        "هنا ترى مسيرتك كما سجّلتها أنت. ما لم تسجّله يظهر «غير مسجّل» ولا يُحسب عليك، وهذه الصفحة لك وحدك.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun UpdateFailedNote(onRetry: () -> Unit) {
    YaqeenCard(containerColor = MaterialTheme.colorScheme.surfaceVariant) {
        Text("تعذّر تحديث الملخّص من سجلاتك، فقد لا تظهر آخر تسجيلاتك في الأرقام.", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = onRetry) { Text("إعادة المحاولة") }
    }
}

@Composable
private fun PeriodSelector(period: Int, onPeriod: (Int) -> Unit) {
    val options = JourneyConfig.PERIODS
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, days ->
            SegmentedButton(
                selected = period == days,
                onClick = { onPeriod(days) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
                label = { Text(when (days) { 7 -> "٧ أيام"; 30 -> "٣٠ يومًا"; else -> "٩٠ يومًا" }) },
            )
        }
    }
}

@Composable
private fun EncouragementCard(text: String, period: PeriodRange) {
    YaqeenCard(containerColor = YaqeenTheme.extra.goldContainer) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(4.dp))
        Text(
            "الفترة المعروضة: ${JourneyFormat.range(period.from, period.to)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatLine(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RatioLine(label: String, progress: Float, caption: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        ProgressBar(progress)
        Spacer(Modifier.height(2.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Unrecorded(text: String = "غير مسجّل في هذه الفترة.") {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private val ind: (Int) -> String = { JourneyFormat.n(it) }

// ------------------------------------------------------------ البطاقات

@Composable
private fun StreakCard(d: JourneyData) {
    Column {
        SectionTitle("الاستمرار")
        YaqeenCard {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StreakFigure("التتابع الحالي", d.streak.current)
                StreakFigure("أطول تتابع", d.streak.best)
            }
            Spacer(Modifier.height(8.dp))
            if (d.streak.bestStart != null && d.streak.bestEnd != null && d.streak.best > 1) {
                Note("أطول تتابع: ${JourneyFormat.range(d.streak.bestStart, d.streak.bestEnd)}.")
            }
            Note("التتابع = أيام متصلة سُجّل فيها أي نشاط عبادي (صلاة أو سنة أو ذكر أو قراءة). اليوم الذي لم يُسجَّل فيه شيء بعد لا يقطع التتابع، وانقطاعه لا يعني تقصيرًا، فقد يكون عدم تسجيل فقط.")
        }
    }
}

@Composable
private fun StreakFigure(label: String, days: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(n(days), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text(
            if (days == 0) "—" else ArabicNoun.DAY.count(days, ind),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HeatmapCard(d: JourneyData) {
    Column {
        SectionTitle("خريطة نشاطك (١٣ أسبوعًا)")
        YaqeenCard { ActivityHeatmap(d.heatmap) }
    }
}

@Composable
private fun ChartsCard(d: JourneyData) {
    Column {
        SectionTitle("آخر ٧ أيام")
        YaqeenCard {
            WeekBarsChart(d.weekBars)
            Spacer(Modifier.height(4.dp))
            Note("ارتفاع العمود = عدد العبادات التي سجّلتها في اليوم (صلوات وسنن وأقسام أذكار وقراءة). الدائرة المجوّفة = غير مسجّل.")
        }
        SectionTitle("آخر ٣٠ يومًا")
        YaqeenCard {
            MonthLineChart(d.monthSeries)
            Spacer(Modifier.height(4.dp))
            Note("الخط يصل بين الأيام المسجّلة فقط، والأيام غير المسجّلة تظهر دوائر مجوّفة ولا تُعدّ نقصًا.")
        }
    }
}

@Composable
private fun ComparisonCard(d: JourneyData, period: Int) {
    Column {
        SectionTitle("مقارنة بالفترة السابقة")
        YaqeenCard {
            if (!d.comparison.hasPrevious) {
                Unrecorded("لا توجد تسجيلات كافية في الفترة السابقة (${ArabicNoun.DAY.count(period, ind)}) لإجراء مقارنة.")
            } else {
                val shown = d.comparison.metrics.filter { it.current > 0 || it.previous > 0 }
                if (shown.isEmpty()) Unrecorded("لا تسجيلات في الفترتين لمقارنتها.")
                shown.forEach { m ->
                    Column(Modifier.padding(vertical = 4.dp)) {
                        Text(m.metric.label, style = MaterialTheme.typography.titleSmall)
                        Text(m.phrase(ind), style = MaterialTheme.typography.bodyMedium)
                        Note("الآن ${n(m.current)} · السابقة ${n(m.previous)}")
                    }
                }
                Spacer(Modifier.height(4.dp))
                Note("المقارنة بين هذه الفترة والفترة التي قبلها بالطول نفسه، للاطلاع فقط.")
            }
        }
    }
}

@Composable
private fun QuranCard(d: JourneyData) {
    val q = d.quran
    Column {
        SectionTitle("القرآن")
        YaqeenCard {
            if (q.daysRead == 0) Unrecorded("لا قراءة مسجّلة في هذه الفترة.")
            else {
                StatLine("أيام القراءة", ArabicNoun.DAY.count(q.daysRead, ind))
                StatLine("الصفحات", ArabicNoun.PAGE.count(q.pages, ind))
                StatLine("ما يعادل", "${JourneyFormat.decimal1(q.juzEquivalent)} جزء")
                if (q.ayahs > 0) StatLine("الآيات", ArabicNoun.AYAH.count(q.ayahs, ind))
            }
            Spacer(Modifier.height(8.dp))
            val w = d.wird
            if (w.daysWithLog == 0) {
                if (d.plans.active > 0) Note("الورد: لا سجل ورد في هذه الفترة (غير مسجّل).")
            } else {
                RatioLine(
                    "التزام الورد",
                    (w.ratio ?: 0.0).toFloat(),
                    "بلغت مقدارك في ${ArabicNoun.DAY.count(w.daysTargetMet, ind)} من ${ArabicNoun.DAY.count(w.daysWithLog, ind)} سُجّل فيها الورد (${percent(w.ratio ?: 0.0)}). الأيام التي لم تسجّل فيها الورد لا تدخل في الحساب.",
                )
            }
            val p = d.plans
            if (p.active + p.paused + p.completed > 0) {
                Spacer(Modifier.height(4.dp))
                StatLine("أوراد جارية", n(p.active))
                StatLine("أوراد مكتملة", n(p.completed))
                if (p.paused > 0) StatLine("أوراد متوقفة", n(p.paused))
                p.activeRemainingPages?.let { StatLine("المتبقي في الأوراد الجارية", ArabicNoun.PAGE.count(it, ind)) }
            }
        }
    }
}

@Composable
private fun AdhkarCard(d: JourneyData) {
    val a = d.adhkar
    Column {
        SectionTitle("الأذكار")
        YaqeenCard {
            if (a.recordedDays == 0) Unrecorded("لا أذكار مسجّلة في هذه الفترة.")
            else {
                fun cap(days: Int) = "${ArabicNoun.DAY.count(days, ind)} من ${ArabicNoun.DAY.count(a.recordedDays, ind)} سجّلت فيها أذكارًا"
                RatioLine("أذكار الصباح مكتملة", (a.rate(a.morningDays) ?: 0.0).toFloat(), cap(a.morningDays))
                RatioLine("أذكار المساء مكتملة", (a.rate(a.eveningDays) ?: 0.0).toFloat(), cap(a.eveningDays))
                RatioLine("أذكار النوم مكتملة", (a.rate(a.sleepDays) ?: 0.0).toFloat(), cap(a.sleepDays))
                StatLine("أذكار أتممتها", ArabicNoun.ITEM.count(a.itemsCompleted, ind))
                Note("يُعدّ القسم مكتملًا في يوم حين تُتمّ كل أذكاره. القسم غير المسجّل في يوم لا يُحسب تقصيرًا.")
            }
        }
    }
}

@Composable
private fun PrayersCard(d: JourneyData) {
    val p = d.prayers
    Column {
        SectionTitle("الصلوات")
        YaqeenCard {
            if (p.prayed == 0 && p.markedNotYet == 0) Unrecorded("لا صلوات مسجّلة في هذه الفترة.")
            else {
                StatLine("صلوات سجّلت أداءها", ArabicNoun.PRAYER.count(p.prayed, ind))
                if (p.prayed > 0) {
                    StatLine("في المسجد جماعة", "${n(p.mosque)} (${percent(p.mosqueShare ?: 0.0)})")
                    StatLine("في البيت", "${n(p.home)} (${percent(p.homeShare ?: 0.0)})")
                    ProgressBar((p.mosqueShare ?: 0.0).toFloat(), Modifier.padding(vertical = 6.dp))
                }
                if (p.markedNotYet > 0) StatLine("سجّلت فيها «لم أصلِّ بعد»", n(p.markedNotYet))
                StatLine("أيام سجّلت فيها صلاة", ArabicNoun.DAY.count(p.daysRecorded, ind))
            }
            if (p.unrecordedSlots > 0) {
                Spacer(Modifier.height(4.dp))
                Note("غير مسجّل: ${n(p.unrecordedSlots)} خانة منذ بدأت التسجيل. هذا لا يعني أنها لم تُؤدَّ، فقط لم تُسجَّل.")
            }
        }
    }
}

@Composable
private fun RawatibCard(d: JourneyData) {
    val r = d.rawatib
    Column {
        SectionTitle("السنن الرواتب")
        YaqeenCard {
            if (r.daysDone == 0) Unrecorded("لا سنن مسجّلة في هذه الفترة.")
            else {
                StatLine("الركعات", ArabicNoun.RAKAA.count(r.rakaat, ind))
                StatLine("أيام أديت فيها سننًا", ArabicNoun.DAY.count(r.daysDone, ind))
                r.consistency?.let {
                    RatioLine("الاستمرار", it.toFloat(), "${ArabicNoun.DAY.count(r.daysDone, ind)} من ${ArabicNoun.DAY.count(r.activeDays, ind)} سجّلت فيها نشاطًا عباديًا (${percent(it)})")
                }
            }
        }
    }
}

@Composable
private fun TasksCard(d: JourneyData) {
    val t = d.tasks
    Column {
        SectionTitle("المهام")
        YaqeenCard {
            if (t.total == 0) Unrecorded("لا مهام مسجّلة في هذه الفترة.")
            else {
                StatLine("المهام المجدولة", ArabicNoun.TASK.count(t.total, ind))
                StatLine("المنجزة", ArabicNoun.TASK.count(t.done, ind))
                t.completion?.let {
                    RatioLine("نسبة الإنجاز", it.toFloat(), "${percent(it)} في ${ArabicNoun.DAY.count(t.daysCounted, ind)} سجّلت فيها نشاطًا. الأيام التي لم تفتح فيها شيئًا لا تُحتسب.")
                }
                t.bestWindow?.let {
                    StatLine("أفضل أسبوع", "${JourneyFormat.range(it.from, it.to)} · ${percent(it.ratio)}")
                }
                t.bestDay?.let {
                    StatLine("أفضل يوم", "${JourneyFormat.shortDate(it.to)} · ${n(it.done)} من ${n(it.total)}")
                }
            }
        }
    }
}

@Composable
private fun PrivacyNote() {
    Text(
        "إحصاءاتك خاصة بك: لا ترتيب ولا مقارنة بين الناس ولا نقاط، وتُحفَظ على حسابك وحده.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}
