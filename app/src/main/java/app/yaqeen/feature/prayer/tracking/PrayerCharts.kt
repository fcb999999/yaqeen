package app.yaqeen.feature.prayer.tracking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yaqeen.core.ArabicText
import app.yaqeen.core.HijriCalendar
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import java.time.LocalDate

/*
 * رسوم Compose Canvas بسيطة بلا مكتبات: أعمدة أسبوعية، شريط أيام شهري، وشريط مكدّس لتفصيل الصلاة.
 * كل الألوان من MaterialTheme، والرسم يراعي اتجاه RTL (أول يوم على اليمين).
 */

/** شريط أفقي مكدّس: مقاطع (قيمة، لون) بنسبها من مجموعها. */
@Composable
internal fun StackedBar(segments: List<Pair<Int, Color>>, modifier: Modifier = Modifier, barHeight: Dp = 10.dp) {
    val total = segments.sumOf { it.first }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier.fillMaxWidth().height(barHeight)) {
        val r = CornerRadius(size.height / 2f)
        drawRoundRect(track, size = size, cornerRadius = r)
        if (total <= 0) return@Canvas
        val clip = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, r)) }
        clipPath(clip) {
            var offset = 0f
            for ((value, color) in segments) {
                if (value <= 0) continue
                val w = size.width * value / total
                val x = if (rtl) size.width - offset - w else offset
                drawRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height))
                offset += w
            }
        }
    }
}

/** أعمدة أسبوعية: عمود لكل يوم من [dates]، ارتفاعه = عدد الصلوات المسجّلة من ٥ ومقسوم ألوانًا حسب الحالة. */
@Composable
internal fun WeeklyBars(
    dates: List<LocalDate>,
    byDate: Map<LocalDate, PrayerDayStats>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    val mosque = statusAccent(PrayerStatus.MOSQUE)
    val home = statusAccent(PrayerStatus.HOME)
    val notPrayed = statusAccent(PrayerStatus.NOT_PRAYED)
    val chartHeight = 110.dp
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        dates.forEach { date ->
            val day = byDate[date]
            val future = date.isAfter(today)
            val desc = when {
                future -> "${HijriCalendar.dayName(date)}: لم يأتِ بعد"
                day == null || day.recorded == 0 -> "${HijriCalendar.dayName(date)}: لا تسجيل"
                else -> "${HijriCalendar.dayName(date)}: سُجّلت ${ArabicText.indic(day.recorded)} من ٥، منها ${ArabicText.indic(day.mosque)} في المسجد"
            }
            Column(
                Modifier.weight(1f).semantics { contentDescription = desc },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    if (day != null && day.recorded > 0) ArabicText.indic(day.recorded) else " ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Canvas(Modifier.fillMaxWidth().height(chartHeight)) {
                    val barWidth = size.width * 0.62f
                    val left = (size.width - barWidth) / 2f
                    val r = CornerRadius(barWidth / 3f)
                    drawRoundRect(if (future) track.copy(alpha = 0.45f) else track, Offset(left, 0f), Size(barWidth, size.height), r)
                    if (day != null) {
                        val unit = size.height / Prayer.entries.size
                        var bottom = size.height
                        for ((count, color) in listOf(day.mosque to mosque, day.home to home, day.notPrayed to notPrayed)) {
                            if (count <= 0) continue
                            val h = unit * count
                            drawRoundRect(color, Offset(left, bottom - h), Size(barWidth, h), CornerRadius(minOf(r.x, h / 2f)))
                            bottom -= h
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    HijriCalendar.dayName(date),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    maxLines = 1,
                    softWrap = false,
                    textAlign = TextAlign.Center,
                    color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    ArabicText.indic(date.dayOfMonth),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (date == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** شريط أيام (شهر/٣٠/٩٠ يومًا): خلية لكل يوم تتدرّج شدّتها بعدد الصلوات المؤداة المسجّلة. */
@Composable
internal fun DayStrip(
    dates: List<LocalDate>,
    byDate: Map<LocalDate, PrayerDayStats>,
    today: LocalDate,
    modifier: Modifier = Modifier,
) {
    val cols = if (dates.size > 40) 15 else 10
    val rows = (dates.size + cols - 1) / cols
    val gap = 4.dp
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val future = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    val prayedColor = MaterialTheme.colorScheme.primary
    val notPrayedColor = MaterialTheme.colorScheme.outline
    val todayRing = MaterialTheme.colorScheme.tertiary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cell = (maxWidth - gap * (cols - 1)) / cols
        val height = cell * rows + gap * (rows - 1).coerceAtLeast(0)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = "شريط الأيام: لكل يوم خلية تزداد قوة لونها كلما زادت الصلوات المسجّلة" },
        ) {
            val cellPx = cell.toPx()
            val gapPx = gap.toPx()
            val radius = CornerRadius(cellPx * 0.22f)
            dates.forEachIndexed { i, date ->
                val col = i % cols
                val row = i / cols
                val x = if (rtl) size.width - (col + 1) * cellPx - col * gapPx else col * (cellPx + gapPx)
                val y = row * (cellPx + gapPx)
                val day = byDate[date]
                val color = when {
                    date.isAfter(today) -> future
                    day == null || day.recorded == 0 -> empty
                    day.prayed == 0 -> notPrayedColor.copy(alpha = 0.55f)
                    else -> prayedColor.copy(alpha = 0.25f + 0.75f * (day.prayed / Prayer.entries.size.toFloat()))
                }
                drawRoundRect(color, Offset(x, y), Size(cellPx, cellPx), radius)
                if (date == today) {
                    drawRoundRect(
                        todayRing, Offset(x, y), Size(cellPx, cellPx), radius,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                }
            }
        }
    }
}

/** مفتاح ألوان بسيط تحت الرسم. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun ChartLegend(items: List<Pair<String, Color>>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEach { (label, color) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(Modifier.size(10.dp)) { drawCircle(color) }
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
