package app.yaqeen.feature.calendar

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.yaqeen.core.ArabicText
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.Sunnah
import app.yaqeen.feature.tasks.ArabicFormat
import app.yaqeen.feature.tasks.TasksRoutes
import app.yaqeen.ui.components.EmptyState
import app.yaqeen.ui.components.LoadingBox
import app.yaqeen.ui.components.Pill
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTheme
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun DayDetailScreen(nav: NavHostController, date: LocalDate) {
    val vm = containerViewModel { DayDetailVm(it) }
    LaunchedEffect(date) { vm.setDate(date) }
    val ui by vm.ui.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("تفاصيل اليوم", onBack = { nav.popBackStack() })
        when (val s = ui) {
            DayUi.Loading -> LoadingBox()
            DayUi.Failed -> EmptyState("تعذّر تحميل اليوم", "حاول مرة أخرى بعد قليل.", Icons.Filled.CalendarMonth)
            is DayUi.Ready -> Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DayBody(nav, vm, s.d)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DayBody(nav: NavHostController, vm: DayDetailVm, d: DayDetail) {
    val today = vm.today()
    val delta = ChronoUnit.DAYS.between(today, d.date).toInt()

    YaqeenCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shift(-1) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "اليوم السابق") }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.label, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Pill(ArabicFormat.relativeDays(delta))
            }
            IconButton(onClick = { vm.shift(1) }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "اليوم التالي") }
        }
    }

    // المناسبات الإسلامية
    if (d.islamic.isNotEmpty()) {
        YaqeenCard(Modifier.fillMaxWidth(), containerColor = YaqeenTheme.extra.goldContainer) {
            SectionTitle("مناسبات إسلامية")
            d.islamic.forEach { e ->
                Column(Modifier.padding(vertical = 4.dp)) {
                    Text(e.nameAr, style = MaterialTheme.typography.titleMedium)
                    if (e.isRange) {
                        Text(
                            "من ${e.hijri.format()} إلى ${ArabicText.indic(app.yaqeen.core.HijriCalendar.fromGregorian(e.endDate!!, d.adjust).day)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (e.note.isNotBlank()) Text(e.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "بحسب تقويم أم القرى؛ وقد يختلف بيومٍ تبعًا لرؤية الهلال.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    // الأحداث الشخصية
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("أحداثي") {
            TextButton(onClick = { nav.navigate(CalendarRoutes.event(date = d.date)) }) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("إضافة حدث")
            }
        }
        if (d.personal.isEmpty()) {
            Text("لا أحداث شخصية في هذا اليوم.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        d.personal.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clickable { nav.navigate(CalendarRoutes.event(e.id, d.date)) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.bodyLarge)
                    val time = ArabicFormat.time12(e.timeOfDay)
                    if (time != null) Text(time, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (e.notes.isNotBlank()) Text(e.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (e.reminderEnabled) Icon(Icons.Filled.NotificationsActive, contentDescription = "التذكير مفعّل", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            }
        }
    }

    // المهام
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("المهام")
        if (d.tasks.isEmpty()) {
            Text("لا مهام في هذا اليوم.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(
                "المنجز ${ArabicText.indic(d.tasks.count { it.done })} من ${ArabicText.indic(d.tasks.size)}",
                style = MaterialTheme.typography.labelLarge,
            )
            d.tasks.forEach { t ->
                StatusRow(t.task.title, t.done, t.detail ?: if (t.derived) "تلقائي" else null)
            }
        }
        if (!d.date.isAfter(today)) {
            TextButton(onClick = { nav.navigate(TasksRoutes.day(d.date)) }) { Text("فتح مهام هذا اليوم") }
        }
    }

    // الصلوات
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("الصلوات")
        Prayer.values().forEach { p ->
            val log = d.worship.prayers.firstOrNull { !it.deleted && it.prayer == p.name }
            val st = log?.let { runCatching { PrayerStatus.valueOf(it.status) }.getOrNull() }
            val ok = st == PrayerStatus.MOSQUE || st == PrayerStatus.HOME
            StatusRow(p.ar, ok, when (st) {
                PrayerStatus.MOSQUE -> "في المسجد جماعة"
                PrayerStatus.HOME -> "في البيت"
                PrayerStatus.NOT_PRAYED -> "لم تُسجَّل كمؤدّاة"
                null -> "غير مسجّلة"
            })
        }
        TextButton(onClick = { runCatching { nav.navigate(Routes.PRAYER) } }) { Text("فتح الصلاة") }
    }

    // السنن
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("السنن الرواتب")
        val done = d.worship.rawatib.filter { !it.deleted && it.done }.map { it.sunnahId }.toSet()
        Sunnah.values().forEach { s ->
            val ok = s.name in done
            StatusRow(s.ar, ok, if (ok) "مسجّلة" else "غير مسجّلة")
        }
    }

    // الأذكار
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("الأذكار")
        if (d.adhkar.isEmpty()) {
            Text("تعذّر تحميل الأذكار.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        d.adhkar.forEach { a ->
            val detail = when {
                a.total > 0 && a.done == a.total -> "مكتملة"
                a.done == 0 -> "غير مسجّلة"
                else -> "${ArabicText.indic(a.done)} من ${ArabicText.indic(a.total)}"
            }
            Row(Modifier.fillMaxWidth().clickable { runCatching { nav.navigate(Routes.adhkarCategory(a.categoryId)) } }) {
                StatusRow(a.title, a.total > 0 && a.done == a.total, detail)
            }
        }
    }

    // الورد
    YaqeenCard(Modifier.fillMaxWidth()) {
        SectionTitle("الورد القرآني")
        if (d.summary.wird.isEmpty()) {
            Text("لا ورد نشط.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        d.summary.wird.forEach { w ->
            StatusRow(w.planName, w.met, w.remainingText ?: "تم بلوغ المقدار اليومي")
        }
        val pages = d.worship.quranDayLog?.pagesRead ?: 0
        if (pages > 0) {
            Spacer(Modifier.height(4.dp))
            Text("إجمالي القراءة في اليوم: ${ArabicFormat.pages(pages)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatusRow(title: String, done: Boolean, detail: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = if (done) "مسجّل" else "غير مسجّل",
            modifier = Modifier.size(20.dp),
            tint = if (done) YaqeenTheme.extra.success else MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
