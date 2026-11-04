package app.yaqeen.feature.quran.plans

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.yaqeen.AppContainer
import app.yaqeen.core.ArabicText
import app.yaqeen.core.dayKey
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.domain.model.QuranPlanUnit
import app.yaqeen.ui.components.SectionTitle
import app.yaqeen.ui.components.SwitchRow
import app.yaqeen.ui.components.YaqeenTopBar
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

// ------------------------------------------------------------------ محرر الورد

@Composable
internal fun PlanEditorScreen(
    c: AppContainer, math: PlanMath, existing: QuranPlanEntity?, today: LocalDate,
    onClose: () -> Unit, onSaved: (String) -> Unit,
) {
    val q = math.q
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var unit by remember { mutableStateOf(existing?.unitEnum() ?: QuranPlanUnit.PAGE) }
    var startAyah by remember { mutableIntStateOf(existing?.startAyahId ?: 1) }
    var endAyah by remember { mutableIntStateOf(existing?.endAyahId ?: q.totalAyahs) }
    var startDate by remember { mutableStateOf(existing?.startDate?.let(LocalDate::parse) ?: today) }
    var hasEnd by remember { mutableStateOf(existing == null || existing.endDate != null) }
    var endDate by remember { mutableStateOf(existing?.endDate?.let(LocalDate::parse) ?: today.plusDays(29)) }
    var dailyText by remember { mutableStateOf((existing?.dailyAmount ?: 1).toString()) }
    var dailyTouched by remember { mutableStateOf(existing != null) }
    var reminderOn by remember { mutableStateOf(existing?.reminderEnabled ?: false) }
    var reminderTime by remember { mutableStateOf(existing?.reminderTime) }
    var showTime by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    val rangeValid = endAyah >= startAyah
    val total = if (rangeValid) math.unitCount(unit, startAyah, endAyah) else 0
    val effectiveStart = if (startDate.isAfter(today)) startDate else today
    val days = if (hasEnd) PlanMath.daysInclusive(effectiveStart, endDate) else 0
    val suggested = if (hasEnd && days > 0 && total > 0) PlanMath.suggestedDaily(total, days) else null

    // الاقتراح التلقائي ما لم يعدّل المستخدم المقدار بنفسه.
    LaunchedEffect(suggested, dailyTouched) {
        if (!dailyTouched && suggested != null) dailyText = suggested.toString()
    }

    val daily = WirdDigits.parse(dailyText)
    val nameOk = name.trim().isNotEmpty()
    val datesOk = !hasEnd || !endDate.isBefore(startDate)
    val dailyOk = daily != null && daily >= 1
    val timeOk = !reminderOn || reminderTime != null
    val canSave = nameOk && rangeValid && datesOk && dailyOk && timeOk && !saving

    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar(if (existing == null) "ورد جديد" else "تعديل الورد", onBack = onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60) }, singleLine = true,
                label = { Text("اسم الورد") }, placeholder = { Text("مثال: ورد رجب") },
                isError = !nameOk, modifier = Modifier.fillMaxWidth(),
            )

            SectionTitle("وحدة الورد")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuranPlanUnit.values().forEach { u ->
                    FilterChip(selected = unit == u, onClick = { unit = u; dailyTouched = false }, label = { Text(u.ar) })
                }
            }

            SectionTitle("نقطة البداية")
            AyahLocationPicker(math, startAyah, isEnd = false) { startAyah = it }
            SectionTitle("نقطة النهاية")
            AyahLocationPicker(math, endAyah, isEnd = true) { endAyah = it }
            if (!rangeValid) Text("نهاية الورد يجب ألّا تسبق بدايته.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            else Text(
                "إجمالي الورد: ${PlanMath.unitText(unit, total)} • ${math.rangeLabel(startAyah, endAyah)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            SectionTitle("المدة")
            DateField("تاريخ البداية", startDate) { startDate = it }
            SwitchRow("تحديد تاريخ نهاية", hasEnd, { hasEnd = it })
            if (hasEnd) {
                DateField("تاريخ النهاية", endDate) { endDate = it }
                if (!datesOk) Text("تاريخ النهاية يجب ألّا يسبق تاريخ البداية.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            SectionTitle("المقدار اليومي")
            OutlinedTextField(
                value = dailyText,
                onValueChange = { dailyText = WirdDigits.sanitize(it); dailyTouched = true },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                label = { Text(when (unit) { QuranPlanUnit.PAGE -> "عدد الصفحات يوميًا"; QuranPlanUnit.JUZ -> "عدد الأجزاء يوميًا"; QuranPlanUnit.AYAH -> "عدد الآيات يوميًا" }) },
                isError = !dailyOk, modifier = Modifier.fillMaxWidth(),
            )
            if (suggested != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "المقترح لإنهاء الورد في ${ArabicText.indic(days)} يومًا: ${PlanMath.unitText(unit, suggested)} يوميًا",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    if (dailyText != suggested.toString()) {
                        TextButton(onClick = { dailyText = suggested.toString(); dailyTouched = false }) { Text("استخدم") }
                    }
                }
            }

            SectionTitle("التذكير")
            SwitchRow("تذكير يومي بالورد", reminderOn, { on ->
                reminderOn = on
                if (on && reminderTime == null) showTime = true
            })
            if (reminderOn) {
                OutlinedButton(onClick = { showTime = true }) {
                    Text(reminderTime?.let { "وقت التذكير: ${PlanFormat.time12(it)}" } ?: "اختر وقت التذكير")
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("إلغاء") }
            Button(
                enabled = canSave,
                onClick = {
                    saving = true
                    scope.launch {
                        val draft = QuranPlanEntity(
                            id = existing?.id ?: "", name = name.trim(),
                            startDate = startDate.dayKey(), endDate = if (hasEnd) endDate.dayKey() else null,
                            unit = unit.name, startAyahId = startAyah, endAyahId = endAyah,
                            dailyAmount = daily!!, progressAyahId = existing?.progressAyahId ?: (startAyah - 1),
                            reminderEnabled = reminderOn && reminderTime != null, reminderTime = reminderTime,
                            status = existing?.status ?: "ACTIVE",
                        )
                        val repo = PlanRepository.of(c)
                        val id = if (existing == null) repo.create(draft) else { repo.update(draft); existing.id }
                        onSaved(id)
                    }
                },
                modifier = Modifier.weight(1f),
            ) { Text("حفظ") }
        }
    }

    if (showTime) {
        WirdTimeDialog(
            initial = reminderTime,
            onConfirm = { reminderTime = it; showTime = false },
            onDismiss = { showTime = false; if (reminderTime == null) reminderOn = false },
        )
    }
}

/** أدوات الأرقام المدخلة (تقبل الهندية). */
internal object WirdDigits {
    fun sanitize(s: String): String = PlanFormat.toLatinDigits(s).filter { it in '0'..'9' }.take(5)
    fun parse(s: String): Int? = sanitize(s).toIntOrNull()
}

// ------------------------------------------------------------------ اختيار موضع في المصحف

private enum class LocMode(val label: String) { SURAH("سورة وآية"), PAGE("صفحة"), JUZ("جزء") }

/**
 * يختار آية بدقة: (سورة + رقم آية) أو صفحة أو جزء. للبداية تُحسب أول آية في الصفحة/الجزء، وللنهاية آخر آية فيهما.
 * القيمة الخارجة معرّف عام للآية دائمًا.
 */
@Composable
internal fun AyahLocationPicker(math: PlanMath, ayahId: Int, isEnd: Boolean, onChange: (Int) -> Unit) {
    val q = math.q
    val ayah = q.ayah(ayahId.coerceIn(1, q.totalAyahs))
    var mode by remember { mutableStateOf(LocMode.SURAH) }
    var surah by remember { mutableIntStateOf(ayah.surah) }
    var ayahText by remember { mutableStateOf(ayah.number.toString()) }
    var pageText by remember { mutableStateOf(ayah.page.toString()) }
    var juzText by remember { mutableStateOf(ayah.juz.toString()) }
    var showSurahs by remember { mutableStateOf(false) }

    fun resolve(): Int? = when (mode) {
        LocMode.SURAH -> WirdDigits.parse(ayahText)?.takeIf { it in 1..q.surah(surah).ayahCount }?.let { q.ayah(surah, it).id }
        LocMode.PAGE -> WirdDigits.parse(pageText)?.takeIf { it in 1..q.pageCount }?.let { if (isEnd) math.lastAyahOfPage(it) else math.firstAyahOfPage(it) }
        LocMode.JUZ -> WirdDigits.parse(juzText)?.takeIf { it in 1..30 }?.let { if (isEnd) math.lastAyahOfJuz(it) else math.firstAyahOfJuz(it) }
    }
    val resolved = resolve()
    fun push() { resolve()?.let(onChange) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LocMode.values().forEach { m ->
                FilterChip(selected = mode == m, onClick = {
                    mode = m
                    // عند تبديل الوضع نبدأ من الموضع الحالي.
                    val cur = q.ayah(ayahId.coerceIn(1, q.totalAyahs))
                    surah = cur.surah; ayahText = cur.number.toString(); pageText = cur.page.toString(); juzText = cur.juz.toString()
                    push()
                }, label = { Text(m.label) })
            }
        }
        when (mode) {
            LocMode.SURAH -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showSurahs = true }, modifier = Modifier.weight(1.4f)) { Text("سورة ${SurahNames.plain(q.surah(surah).nameAr)}") }
                OutlinedTextField(
                    value = ayahText, onValueChange = { ayahText = WirdDigits.sanitize(it); push() },
                    singleLine = true, label = { Text("الآية (١–${ArabicText.indic(q.surah(surah).ayahCount)})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = resolved == null, modifier = Modifier.weight(1f),
                )
            }
            LocMode.PAGE -> OutlinedTextField(
                value = pageText, onValueChange = { pageText = WirdDigits.sanitize(it); push() },
                singleLine = true, label = { Text("رقم الصفحة (١–${ArabicText.indic(q.pageCount)})") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = resolved == null, modifier = Modifier.fillMaxWidth(),
            )
            LocMode.JUZ -> OutlinedTextField(
                value = juzText, onValueChange = { juzText = WirdDigits.sanitize(it); push() },
                singleLine = true, label = { Text("رقم الجزء (١–٣٠)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = resolved == null, modifier = Modifier.fillMaxWidth(),
            )
        }
        val shown = q.ayah(ayahId.coerceIn(1, q.totalAyahs))
        Text(
            "${if (isEnd) "تنتهي" else "تبدأ"} عند: ${math.ayahLabel(shown.id)} • ص ${ArabicText.indic(shown.page)} • الجزء ${ArabicText.indic(shown.juz)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showSurahs) {
        SurahPickerDialog(math, onDismiss = { showSurahs = false }) { s ->
            surah = s
            ayahText = if (isEnd) q.surah(s).ayahCount.toString() else "1"
            showSurahs = false
            push()
        }
    }
}

@Composable
internal fun SurahPickerDialog(math: PlanMath, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val q = math.q
    var query by remember { mutableStateOf("") }
    val list = remember(query) {
        val t = ArabicText.normalize(PlanFormat.toLatinDigits(query))
        if (t.isBlank()) q.surahs else q.surahs.filter { ArabicText.normalize(it.nameAr).contains(t) || it.number.toString() == t }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر السورة") },
        text = {
            Column {
                OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("بحث بالاسم أو الرقم") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.number }) { s ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(s.number) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(ArabicText.indic(s.number), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(40.dp))
                            Text(SurahNames.plain(s.nameAr), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Text("${ArabicText.indic(s.ayahCount)} آية", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

// ------------------------------------------------------------------ التاريخ والوقت

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: LocalDate, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${PlanFormat.dateShort(value)}")
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("تأكيد") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("إلغاء") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WirdTimeDialog(initial: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val (h, m) = PlanMath.parseTime(initial) ?: (5 to 0)
    val state = rememberTimePickerState(initialHour = h, initialMinute = m, is24Hour = false)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("وقت التذكير") },
        text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state = state) } },
        confirmButton = { TextButton(onClick = { onConfirm(PlanMath.formatTime(state.hour, state.minute)) }) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** ضبط موضع التقدّم: آخر آية أُنجزت (أي أن الورد يُعدّ مقروءًا حتى هذه الآية شاملةً). */
@Composable
internal fun SetPositionDialog(math: PlanMath, plan: QuranPlanEntity, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    var ayah by remember { mutableIntStateOf(plan.progressAyahId.coerceIn(plan.startAyahId, plan.endAyahId)) }
    val valid = ayah in plan.startAyahId..plan.endAyahId
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ضبط موضع التقدّم") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("اختر آخر آية قرأتَها من هذا الورد (تُعدّ مقروءة).", style = MaterialTheme.typography.bodyMedium)
                AyahLocationPicker(math, ayah, isEnd = true) { ayah = it }
                if (!valid) Text("الموضع خارج مدى الورد.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = { onConfirm(plan.startAyahId - 1) }) { Text("إعادة التقدّم إلى البداية") }
            }
        },
        confirmButton = { TextButton(enabled = valid, onClick = { onConfirm(ayah) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
