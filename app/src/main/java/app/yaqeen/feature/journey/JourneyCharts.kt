package app.yaqeen.feature.journey

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * رسوم Canvas بسيطة بلا مكتبات. الاتجاه: في RTL الأقدم يمينًا والأحدث يسارًا (يتبع اتجاه القراءة).
 * اليوم "غير المسجّل" لا يُرسم صفرًا ساقطًا بل علامة محايدة (دائرة مجوّفة) حتى لا يبدو تقصيرًا.
 */

private fun levelColor(level: Int, base: Color, empty: Color): Color = when (level) {
    0 -> empty
    1 -> base.copy(alpha = 0.28f)
    2 -> base.copy(alpha = 0.50f)
    3 -> base.copy(alpha = 0.75f)
    else -> base
}

/** أعمدة آخر 7 أيام. */
@Composable
fun WeekBarsChart(points: List<DayPoint>, modifier: Modifier = Modifier) {
    val bar = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val desc = "أعمدة آخر ٧ أيام: " + points.joinToString("، ") {
        "${JourneyFormat.shortDayName(it.date)} " + (it.units?.let { u -> "${JourneyFormat.n(u)} عبادة مسجّلة" } ?: "غير مسجّل")
    }
    Column(modifier.semantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(120.dp)) {
            val n = points.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = slot * 0.5f
            val maxUnits = JourneyConfig.MAX_DAILY_UNITS.toFloat()
            points.forEachIndexed { i, p ->
                val idx = if (rtl) n - 1 - i else i
                val cx = slot * (idx + 0.5f)
                val u = p.units
                if (u == null) {
                    // غير مسجّل: نقطة مجوّفة على خط الأساس
                    drawCircle(empty, radius = 5.dp.toPx(), center = Offset(cx, size.height - 6.dp.toPx()), style = Stroke(1.5.dp.toPx()))
                } else {
                    val h = ((u / maxUnits) * (size.height - 8.dp.toPx())).coerceAtLeast(6.dp.toPx())
                    drawRoundRect(bar, Offset(cx - barW / 2, size.height - h), Size(barW, h), CornerRadius(6.dp.toPx()))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            points.forEach {
                Text(
                    JourneyFormat.shortDayName(it.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** خط آخر 30 يومًا: يصل بين الأيام المسجّلة المتتالية فقط، والأيام غير المسجّلة علامات مجوّفة. */
@Composable
fun MonthLineChart(points: List<DayPoint>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outlineVariant
    val grid = MaterialTheme.colorScheme.surfaceVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val recorded = points.count { it.units != null }
    val desc = "رسم آخر ٣٠ يومًا: ${JourneyFormat.n(recorded)} يومًا فيها نشاط مسجّل"
    Column(modifier.semantics { contentDescription = desc }) {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val n = points.size.coerceAtLeast(2)
            val padV = 8.dp.toPx()
            val h = size.height - 2 * padV
            val maxUnits = JourneyConfig.MAX_DAILY_UNITS.toFloat()
            fun x(i: Int): Float {
                val idx = if (rtl) points.size - 1 - i else i
                return size.width * (idx + 0.5f) / n
            }
            fun y(u: Int) = padV + h * (1f - u / maxUnits)

            // خطوط إرشادية: الأساس والمنتصف
            for (frac in listOf(0f, 0.5f, 1f)) {
                val gy = padV + h * frac
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1.dp.toPx())
            }
            points.forEachIndexed { i, p ->
                val u = p.units
                if (u != null) {
                    val next = points.getOrNull(i + 1)?.units
                    if (next != null) {
                        drawLine(line, Offset(x(i), y(u)), Offset(x(i + 1), y(next)), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            points.forEachIndexed { i, p ->
                val u = p.units
                if (u != null) drawCircle(line, 3.5.dp.toPx(), Offset(x(i), y(u)))
                else drawCircle(empty, 2.5.dp.toPx(), Offset(x(i), padV + h), style = Stroke(1.dp.toPx()))
            }
        }
        if (points.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(JourneyFormat.shortDate(points.first().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(JourneyFormat.shortDate(points.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** خريطة النشاط (13 أسبوعًا) على نمط GitHub: الأعمدة أسابيع والصفوف أيام الأسبوع من السبت. */
@Composable
fun ActivityHeatmap(grid: HeatmapGrid, modifier: Modifier = Modifier) {
    val base = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    var selected by remember(grid) { mutableStateOf<LocalDate?>(null) }
    val cols = grid.weeks.size.coerceAtLeast(1)
    val gap = 3f

    val activeCount = grid.weeks.sumOf { w -> w.count { it != null && it.level > 0 } }
    val desc = "خريطة النشاط لآخر ${JourneyFormat.n(cols)} أسبوعًا: ${JourneyFormat.n(activeCount)} يومًا فيها نشاط مسجّل"

    Column(modifier.semantics { contentDescription = desc }) {
        BoxWithAspect(cols) { cell ->
            Canvas(
                Modifier.fillMaxWidth().height((cell * 7 + gap * 6).dp)
                    .pointerInput(grid, rtl) {
                        detectTapGestures { pos ->
                            val cw = size.width / cols.toFloat()
                            val colRaw = (pos.x / cw).toInt().coerceIn(0, cols - 1)
                            val col = if (rtl) cols - 1 - colRaw else colRaw
                            val rh = size.height / 7f
                            val row = (pos.y / rh).toInt().coerceIn(0, 6)
                            selected = grid.weeks.getOrNull(col)?.getOrNull(row)?.date
                        }
                    },
            ) {
                val cw = size.width / cols
                val rh = size.height / 7
                val g = gap.dp.toPx()
                grid.weeks.forEachIndexed { w, days ->
                    val colIdx = if (rtl) cols - 1 - w else w
                    days.forEachIndexed { r, cell ->
                        if (cell != null) {
                            val color = levelColor(cell.level, base, empty)
                            drawRoundRect(
                                color,
                                Offset(colIdx * cw + g / 2, r * rh + g / 2),
                                Size(cw - g, rh - g),
                                CornerRadius(3.dp.toPx()),
                            )
                            if (cell.date == selected) {
                                drawRoundRect(
                                    base, Offset(colIdx * cw + g / 2, r * rh + g / 2), Size(cw - g, rh - g),
                                    CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx()),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        val sel = selected?.let { d -> grid.weeks.flatten().firstOrNull { it?.date == d } }
        Text(
            text = when {
                sel == null -> "المس يومًا لترى ما سجّلته فيه"
                sel.level == 0 -> "${JourneyFormat.fullDate(sel.date)}: غير مسجّل"
                else -> "${JourneyFormat.fullDate(sel.date)}: ${JourneyFormat.n(sel.units)} من ${JourneyFormat.n(JourneyConfig.MAX_DAILY_UNITS)} عبادة مسجّلة"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        HeatLegend(base, empty)
    }
}

/** يحسب ارتفاع الخلية من عرض المتاح (مربعة) ثم يمرّره للمحتوى. */
@Composable
private fun BoxWithAspect(cols: Int, content: @Composable (cellDp: Float) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cellDp = maxWidth.value / cols
        content(cellDp)
    }
}

@Composable
private fun HeatLegend(base: Color, empty: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).drawCell(empty))
        Spacer(Modifier.width(4.dp))
        Text("غير مسجّل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text("أقل", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        for (lvl in 1..4) {
            Box(Modifier.size(12.dp).drawCell(levelColor(lvl, base, empty)))
            Spacer(Modifier.width(3.dp))
        }
        Text("أكثر", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun Modifier.drawCell(color: Color): Modifier =
    this.then(Modifier.background(color, androidx.compose.foundation.shape.RoundedCornerShape(3.dp)))
