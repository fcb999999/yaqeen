package app.yaqeen.feature.quran.reader

import android.graphics.Paint
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.yaqeen.data.quran.Ayah
import app.yaqeen.data.quran.QuranData
import app.yaqeen.ui.LocalAppContainer
import app.yaqeen.ui.theme.YaqeenFonts
import app.yaqeen.ui.theme.YaqeenTheme
import kotlin.math.abs
import kotlin.math.min

/**
 * صفحة المصحف كما هي مطبوعة: ترويسة ثم ١٥ سطرًا ثابتًا بكلمات كل سطر في مصحف المدينة، يمتد كل سطر
 * من حافة إلى حافة (توزَّع المسافة بين كلماته)، ثم رقم الصفحة. حجم الخط واحد لكل الصفحات ويُحسب من
 * عرض الشاشة وارتفاعها فتظهر الصفحة كاملة دون تمرير. اللمس يُظهر الأشرطة ويخفيها، والضغط المطوّل على
 * آية يحددها ويفتح خدماتها.
 */
@Composable
fun MushafPage(
    page: Int,
    layout: MushafLayout,
    quran: QuranData,
    prefs: ReaderPrefs,
    palette: ReaderPalette,
    selectedAyahId: Int?,
    focusAyahId: Int?,
    playingAyahId: Int?,
    onAyahLongPress: (Ayah) -> Unit,
    onBackgroundTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val info = remember(page, quran) { PageLayout.info(quran, page) }
    val margin = prefs.pageMargin.dp
    Box(modifier, contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = MUSHAF_MAX_WIDTH).fillMaxSize()) {
            PageHeader(info, palette, margin)
            MushafLines(
                page = page,
                layout = layout,
                quran = quran,
                palette = palette,
                selectedAyahId = selectedAyahId,
                focusAyahId = focusAyahId,
                playingAyahId = playingAyahId,
                onAyahLongPress = onAyahLongPress,
                onBackgroundTap = onBackgroundTap,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = margin),
            )
            PageFooter(page, palette, compact = true)
        }
    }
}

/** أقصى عرض للصفحة: على الأجهزة اللوحية الأفقية تبقى الصفحة بنسبة المصحف المعتادة. */
private val MUSHAF_MAX_WIDTH = 760.dp

/** حجم الخط من ارتفاع السطر (الباقي للتشكيل فوق الحروف وتحتها). */
private const val HEIGHT_RATIO = 0.64f

/** خط الأساس تحت منتصف السطر بهذا الكسر من حجم الخط (منتصف جسم الحروف العربية فوق خط الأساس). */
private const val BASELINE_SHIFT = 0.30f

/** السطر الذي يقل عرضه الطبيعي عن هذا الكسر من العرض يتوسط بدل أن تتباعد كلماته كثيرًا. */
private const val JUSTIFY_MIN_FILL = 0.62f

/** رقم الآية (زخرفة الخط) أكبر قليلًا من الكلمات ليُقرأ بوضوح. */
private const val MARKER_SCALE = 1.22f

/** المسافة المريحة بين الكلمات وأقصاها (مضاعفات أصغر مسافة) قبل أن يُكمَل السطر بالكشيدة. */
private const val GAP_COMFORT = 1.6f
private const val GAP_MAX = 2.6f

/** سماكة إضافية للحروف (حدّ بلونها) بوحدة em. */
private const val TEXT_WEIGHT_EM = 0.014f

/** أقصى عدد كشائد في الكلمة الواحدة. */
private const val MAX_KASHIDA_PER_WORD = 4

private class PlacedToken(val token: MushafToken, val text: String, val x: Float, val width: Float)

private class PlacedLine(val line: MushafLine, val top: Float, val tokens: List<PlacedToken>, val gap: Float, val scaleX: Float)

private class PagePlacement(val lines: List<PlacedLine>, val slotH: Float, val fontPx: Float, val width: Float)

private fun tokenScale(t: MushafToken) = if (t.kind == MushafToken.Kind.AyahEnd) MARKER_SCALE else 1f

/**
 * يوزّع الفراغ الزائد في السطر: أولًا مسافات مريحة بين الكلمات (حتى حدّ أقصى)، ثم كشائد في أطول الكلمات
 * القابلة للمدّ كما في المصحف المطبوع. يعيد نص كل رمز وعرضه الفعلي بعد المدّ.
 */
private fun stretchLine(tokens: List<MushafToken>, fontPx: Float, width: Float, minGap: Float, tatweelPx: Float, paint: Paint): List<Pair<String, Float>> {
    val base = tokens.map { it.text to it.em * fontPx * tokenScale(it) }
    val n = tokens.size
    if (n < 2 || tatweelPx <= 0f) return base
    val natural = base.sumOf { it.second.toDouble() }.toFloat()
    val gapTarget = ((width - natural) / (n - 1)).coerceIn(minGap * GAP_COMFORT, minGap * GAP_MAX)
    val slack = width - natural - gapTarget * (n - 1)
    var k = (slack / tatweelPx).toInt()
    if (k <= 0) return base
    // الكلمات القابلة للمدّ، الأطول أولًا
    val eligible = tokens.indices
        .filter { tokens[it].kind == MushafToken.Kind.Word && Kashida.position(tokens[it].text) >= 0 }
        .sortedByDescending { tokens[it].em }
    if (eligible.isEmpty()) return base
    k = k.coerceAtMost(eligible.size * MAX_KASHIDA_PER_WORD)
    val counts = IntArray(n)
    var i = 0
    while (k > 0) {
        counts[eligible[i % eligible.size]]++
        k--
        i++
    }
    paint.textScaleX = 1f
    paint.textSize = fontPx
    return tokens.mapIndexed { idx, t ->
        if (counts[idx] == 0) base[idx] else {
            val s = Kashida.stretch(t.text, counts[idx])
            s to paint.measureText(s)
        }
    }
}

private fun place(lines: List<MushafLine>, layout: MushafLayout, width: Float, height: Float, centered: Boolean, paint: Paint): PagePlacement {
    val slotH = height / MushafLayout.LINES
    val fontPx = min(slotH * HEIGHT_RATIO, width / layout.lineEm)
    val minGap = layout.minGapEm * fontPx
    val tatweelPx = layout.tatweelEm * fontPx
    // الصفحتان الأوليان أسطرهما أقل فتتوسطان رأسيًا
    val top0 = if (centered) ((height - lines.size * slotH) / 2f).coerceAtLeast(0f) else 0f
    val placed = lines.mapIndexed { i, line ->
        val top = top0 + i * slotH
        if (line !is MushafLine.Words || line.tokens.isEmpty()) return@mapIndexed PlacedLine(line, top, emptyList(), 0f, 1f)
        val n = line.tokens.size
        val natural = line.tokens.sumOf { (it.em * tokenScale(it)).toDouble() }.toFloat() * fontPx
        val tight = natural + minGap * (n - 1)
        var scale = 1f
        val gap: Float
        val start: Float // الطرف الأيمن لأول كلمة
        var parts: List<Pair<String, Float>> = line.tokens.map { it.text to it.em * fontPx * tokenScale(it) }
        when {
            tight > width -> {
                // أطول من العرض (أسطر قليلة): ضغط أفقي خفيف
                scale = width / tight
                parts = parts.map { it.first to it.second * scale }
                gap = minGap * scale
                start = width
            }
            !centered && n > 1 && tight >= width * JUSTIFY_MIN_FILL -> {
                parts = stretchLine(line.tokens, fontPx, width, minGap, tatweelPx, paint)
                val filled = parts.sumOf { it.second.toDouble() }.toFloat()
                gap = ((width - filled) / (n - 1)).coerceAtLeast(minGap * 0.5f)
                start = width
            }
            else -> {
                gap = minGap * 1.4f
                val total = natural + gap * (n - 1)
                start = width - ((width - total) / 2f).coerceAtLeast(0f)
            }
        }
        var cursor = start
        val tokens = line.tokens.mapIndexed { idx, t ->
            val (text, w) = parts[idx]
            val x = cursor - w
            cursor = x - gap
            PlacedToken(t, text, x, w)
        }
        PlacedLine(line, top, tokens, gap, scale)
    }
    return PagePlacement(placed, slotH, fontPx, width)
}

/** الآية تحت نقطة اللمس (أو أقرب كلمة في السطر نفسه)، أو null خارج أسطر الآيات. */
private fun ayahAt(p: PagePlacement, pos: Offset): Int? {
    val line = p.lines.firstOrNull { pos.y >= it.top && pos.y < it.top + p.slotH } ?: return null
    if (line.tokens.isEmpty()) return null
    val hit = line.tokens.firstOrNull { pos.x >= it.x - line.gap / 2f && pos.x <= it.x + it.width + line.gap / 2f }
    return (hit ?: line.tokens.minBy { abs((it.x + it.width / 2f) - pos.x) }).token.ayahId
}

@Composable
private fun MushafLines(
    page: Int,
    layout: MushafLayout,
    quran: QuranData,
    palette: ReaderPalette,
    selectedAyahId: Int?,
    focusAyahId: Int?,
    playingAyahId: Int?,
    onAyahLongPress: (Ayah) -> Unit,
    onBackgroundTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val c = LocalAppContainer.current
    val bookmarks by remember(page) { c.db.bookmarks().observeForPage(page) }.collectAsState(initial = emptyList())
    val bookmarkColors = remember(bookmarks, quran) {
        buildMap<Int, Color> {
            for (b in bookmarks) runCatching {
                val argb = if ((b.color ushr 24) == 0) b.color or (0xFF shl 24) else b.color
                put(quran.ayah(b.surah, b.ayah).id, Color(argb))
            }
        }
    }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply { typeface = MushafLayoutLoader.quranTypeface(context) }
    }
    val longPress by rememberUpdatedState(onAyahLongPress)
    val tap by rememberUpdatedState(onBackgroundTap)
    val lines = remember(page, layout) { layout.lines(page) }
    val noor = YaqeenTheme.isNoor

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val placement = remember(lines, layout, w, h) { place(lines, layout, w, h, centered = page <= 2, paint = paint) }

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(placement) {
                    detectTapGestures(
                        onTap = { tap() },
                        onLongPress = { pos ->
                            val id = ayahAt(placement, pos) ?: return@detectTapGestures
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            longPress(quran.ayah(id))
                        },
                    )
                }
                .drawBehind {
                    drawHighlights(placement, palette, selectedAyahId, focusAyahId, playingAyahId)
                    drawLines(placement, layout, paint, palette, bookmarkColors)
                },
        )

        // ترويسات السور فوق أسطرها (زخرفة + اسم السورة بخط المصحف)
        val slotDp = with(density) { placement.slotH.toDp() }
        for (pl in placement.lines) {
            val header = pl.line as? MushafLine.Header ?: continue
            val surah = quran.surah(header.surah)
            Box(
                Modifier
                    .offset { IntOffset(0, (pl.top + placement.slotH * 0.06f).toInt()) }
                    .fillMaxWidth()
                    .height(slotDp * 0.88f)
                    .drawBehind { if (noor) drawNoorBanner(palette) else drawClassicBanner(palette) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    QuranText.forHafsFont("سُورَةُ ${surah.nameAr}"),
                    style = TextStyle(
                        fontFamily = YaqeenFonts.Quran,
                        fontSize = with(density) { (placement.fontPx * 0.78f).toSp() },
                        lineHeight = with(density) { (placement.fontPx * 1.2f).toSp() },
                        color = palette.text,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}

private fun DrawScope.drawHighlights(p: PagePlacement, palette: ReaderPalette, selected: Int?, focus: Int?, playing: Int?) {
    if (selected == null && focus == null && playing == null) return
    for (line in p.lines) {
        if (line.tokens.isEmpty()) continue
        // كل آية في السطر: من أول كلماتها إلى آخرها مع نصف المسافة على الجانبين
        line.tokens.groupBy { it.token.ayahId }.forEach { (id, toks) ->
            val alpha = when (id) {
                playing -> 0.34f
                selected, focus -> 0.22f
                else -> return@forEach
            }
            val left = (toks.minOf { it.x } - line.gap / 2f).coerceAtLeast(0f)
            val right = (toks.maxOf { it.x + it.width } + line.gap / 2f).coerceAtMost(p.width)
            drawRect(palette.accent.copy(alpha = alpha), topLeft = Offset(left, line.top), size = Size(right - left, p.slotH))
        }
    }
}

private fun DrawScope.drawLines(p: PagePlacement, layout: MushafLayout, paint: Paint, palette: ReaderPalette, bookmarkColors: Map<Int, Color>) {
    val textColor = palette.text.toArgb()
    val accent = palette.accent.toArgb()
    paint.textSize = p.fontPx
    drawIntoCanvas { canvas ->
        val nc = canvas.nativeCanvas
        for (line in p.lines) {
            val baseline = line.top + p.slotH / 2f + p.fontPx * BASELINE_SHIFT
            when (line.line) {
                is MushafLine.Bismillah -> {
                    paint.textScaleX = 1f
                    paint.color = textColor
                    val bw = paint.measureText(layout.bismillah)
                    paint.style = Paint.Style.FILL_AND_STROKE
                    paint.strokeWidth = p.fontPx * TEXT_WEIGHT_EM
                    nc.drawText(layout.bismillah, (p.width - bw) / 2f, baseline, paint)
                    paint.style = Paint.Style.FILL
                }
                is MushafLine.Words -> {
                    paint.textScaleX = line.scaleX
                    for (t in line.tokens) {
                        paint.color = when (t.token.kind) {
                            MushafToken.Kind.Word -> textColor
                            MushafToken.Kind.AyahEnd -> (bookmarkColors[t.token.ayahId]?.toArgb() ?: accent)
                            MushafToken.Kind.HizbMark -> accent
                        }
                        paint.textSize = p.fontPx * tokenScale(t.token)
                        // حدّ رفيع بلون الحروف يعطيها سماكة الحبر في المصحف المطبوع (الكلمات فقط لا الزخارف)
                        if (t.token.kind == MushafToken.Kind.Word) {
                            paint.style = Paint.Style.FILL_AND_STROKE
                            paint.strokeWidth = p.fontPx * TEXT_WEIGHT_EM
                        }
                        nc.drawText(t.text, t.x, baseline, paint)
                        paint.style = Paint.Style.FILL
                    }
                    paint.textSize = p.fontPx
                }
                else -> Unit
            }
        }
        paint.textScaleX = 1f
    }
}
