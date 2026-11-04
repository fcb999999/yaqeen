package app.yaqeen.feature.prayer.tracking

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.yaqeen.core.ArabicText
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import kotlinx.coroutines.launch
import java.time.LocalDate

/*
 * مكونات واجهة مشتركة لتتبع الصلاة: تُستعمل في الصفحة الرئيسية للصلاة والإحصاءات ومحرّر اليوم و PrayerQuickRow.
 * الألوان كلها من MaterialTheme: المسجد = اللون الأساسي، البيت = الذهبي الهادئ، "لم أصلِّ بعد" = محايد (ليس أحمر).
 */

internal fun statusIcon(s: PrayerStatus?): ImageVector = when (s) {
    PrayerStatus.MOSQUE -> Icons.Filled.Mosque
    PrayerStatus.HOME -> Icons.Filled.Home
    PrayerStatus.NOT_PRAYED -> Icons.Filled.HourglassEmpty
    null -> Icons.Filled.RadioButtonUnchecked
}

/** لون التمييز لحالة (للأعمدة والنقاط). null = غير مسجّلة. */
@Composable
internal fun statusAccent(s: PrayerStatus?): Color = when (s) {
    PrayerStatus.MOSQUE -> MaterialTheme.colorScheme.primary
    PrayerStatus.HOME -> MaterialTheme.colorScheme.tertiary
    PrayerStatus.NOT_PRAYED -> MaterialTheme.colorScheme.outline
    null -> MaterialTheme.colorScheme.outlineVariant
}

@Composable
internal fun statusContainer(s: PrayerStatus?): Color = when (s) {
    PrayerStatus.MOSQUE -> MaterialTheme.colorScheme.primaryContainer
    PrayerStatus.HOME -> MaterialTheme.colorScheme.tertiaryContainer
    PrayerStatus.NOT_PRAYED -> MaterialTheme.colorScheme.surfaceVariant
    null -> MaterialTheme.colorScheme.surface
}

@Composable
internal fun statusOnContainer(s: PrayerStatus?): Color = when (s) {
    PrayerStatus.MOSQUE -> MaterialTheme.colorScheme.onPrimaryContainer
    PrayerStatus.HOME -> MaterialTheme.colorScheme.onTertiaryContainer
    PrayerStatus.NOT_PRAYED -> MaterialTheme.colorScheme.onSurfaceVariant
    null -> MaterialTheme.colorScheme.onSurfaceVariant
}

/** خيار حالة واحد (أيقونة + نص كامل) قابل للاختيار. */
@Composable
internal fun StatusChoice(
    status: PrayerStatus,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (selected) statusContainer(status) else MaterialTheme.colorScheme.surface
    val content = if (selected) statusOnContainer(status) else MaterialTheme.colorScheme.onSurfaceVariant
    val border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) statusAccent(status) else MaterialTheme.colorScheme.outlineVariant)
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
        border = border,
        modifier = modifier
            .heightIn(min = 64.dp)
            .semantics { role = Role.RadioButton; this.selected = selected },
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(statusIcon(status), contentDescription = null, Modifier.size(20.dp), tint = if (selected) statusAccent(status) else content)
            Spacer(Modifier.size(4.dp))
            Text(status.ar, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 3)
        }
    }
}

/** ثلاثة خيارات حالة متساوية العرض؛ نقر المختار منها يمسح التسجيل (يتولاه المستدعي في [onToggle]). */
@Composable
internal fun StatusChoiceRow(
    current: PrayerStatus?,
    enabled: Boolean,
    onToggle: (PrayerStatus) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        PrayerStatus.entries.forEach { s ->
            StatusChoice(s, selected = current == s, enabled = enabled, onClick = { onToggle(s) }, modifier = Modifier.weight(1f))
        }
    }
}

/** نقطة حالة صغيرة (لقائمة السجل). */
@Composable
internal fun StatusDot(status: PrayerStatus?, modifier: Modifier = Modifier, dotSize: androidx.compose.ui.unit.Dp = 14.dp) {
    val color = statusAccent(status)
    Canvas(modifier.size(dotSize)) {
        if (status == null) {
            val stroke = 1.5.dp.toPx()
            drawCircle(color, radius = (size.minDimension - stroke) / 2f, style = Stroke(stroke))
        } else {
            drawCircle(color)
        }
    }
}

/** قائمة السنن: خانة اختيار + الاسم + عدد الركعات. */
@Composable
internal fun RawatibChecklist(
    day: RawatibDay,
    enabled: Boolean,
    onToggle: (SunnahItem, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        day.entries.forEachIndexed { index, e ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(value = e.done, enabled = enabled, role = Role.Checkbox, onValueChange = { onToggle(e.item, it) })
                    .heightIn(min = 52.dp)
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = e.done, onCheckedChange = null, enabled = enabled)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(e.item.name, style = MaterialTheme.typography.bodyLarge)
                    if (e.item.isCustom && e.item.archived) {
                        Text("مؤرشفة", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text(
                    PrayerFormat.rakaat(e.rakaat),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * محرّر يوم سابق (لوحة سفلية): حالات الصلوات الخمس + قائمة السنن، مع الكتابة عبر [PrayerTrackingRepository].
 * يُستعمل للتصحيح من سجل الإحصاءات.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PrayerDayEditorSheet(
    repo: PrayerTrackingRepository,
    date: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val statuses by remember(repo, date) { repo.observeDay(date) }.collectAsState(initial = emptyMap())
    val checklist by remember(repo, date) { repo.observeChecklist(date) }.collectAsState(initial = RawatibDay(emptyList()))
    val scope = rememberCoroutineScope()
    val enabled = date <= today

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "تعديل يوم ${PrayerFormat.dayLabel(date, today)}",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                "اختر حالة كل صلاة، ويمكنك النقر على الحالة المختارة لمسح التسجيل.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Prayer.entries.forEach { p ->
                Text(p.ar, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp, bottom = 6.dp))
                StatusChoiceRow(
                    current = statuses[p],
                    enabled = enabled,
                    onToggle = { s -> scope.launch { repo.toggleStatus(date, p, s) } },
                )
            }
            Text("السنن الرواتب", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
            RawatibChecklist(
                day = checklist,
                enabled = enabled,
                onToggle = { item, done -> scope.launch { repo.setSunnahDone(date, item, done) } },
            )
            Text(
                "إجمالي الركعات المسجّلة: ${ArabicText.indic(checklist.totalRakaat)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End).padding(top = 8.dp)) { Text("تم") }
        }
    }
}
