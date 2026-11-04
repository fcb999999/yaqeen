package app.yaqeen.feature.quran.reader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.Ayah
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.Surah
import app.yaqeen.ui.LocalAppContainer
import app.yaqeen.ui.theme.YaqeenFonts
import app.yaqeen.ui.theme.YaqeenTextStyles
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** أقصى عرض لعمود النص: على الأجهزة اللوحية الأسطر الطويلة جدًا تُتعب العين وتترك فراغًا رأسيًا كبيرًا. */
private val MAX_CONTENT_WIDTH = 720.dp

/** طلب تمرير داخل الصفحة إلى آية (nonce يجعل الطلب المتكرر لنفس الآية يُنفَّذ من جديد). */
data class ScrollRequest(val ayahId: Int, val nonce: Int)

/** بيانات صفحة محسوبة مرة واحدة: كتلها ونصوصها المنسّقة ومعلوماتها. */
class PageData(
    val blocks: List<PageBlock>,
    val formatted: List<Pair<String, List<AyahSpan>>?>,
    val info: PageInfo,
)

@Composable
fun rememberPageData(page: Int, quran: QuranData): PageData = remember(page, quran) {
    val blocks = PageLayout.blocks(quran, page)
    PageData(
        blocks = blocks,
        formatted = blocks.map { (it as? PageBlock.AyahRun)?.let { r -> RunFormat.build(r.ayahs) } },
        info = PageLayout.info(quran, page),
    )
}

/**
 * صفحة واحدة من المصحف (1..604) داخل المُمرِّر الأفقي: ترويسة خفيفة ثم نص الآيات بخط المصحف
 * مع ترويسات السور والبسملة ووسوم الأرباع وشارة نهاية كل آية ورقم الصفحة. النص كما هو في البيانات دون تعديل.
 *
 * [fitEnabled]: على الشاشات الكبيرة يُكبَّر الخط تلقائيًا (حتى [FitPage.MAX_SCALE]) ليملأ النص ارتفاع الشاشة
 * بدل أن يترك فراغًا أسفل الآيات. لا يُصغَّر الخط أبدًا عن اختيار المستخدم.
 */
@Composable
fun ReaderPage(
    page: Int,
    quran: QuranData,
    prefs: ReaderPrefs,
    palette: ReaderPalette,
    scroll: ScrollState,
    selectedAyahId: Int?,
    focusAyahId: Int?,
    playingAyahId: Int?,
    scrollRequest: ScrollRequest?,
    onAyahTap: (Ayah) -> Unit,
    onBackgroundTap: () -> Unit,
    modifier: Modifier = Modifier,
    fitEnabled: Boolean = false,
) {
    val data = rememberPageData(page, quran)
    val margin = prefs.pageMargin.dp
    val onBg by rememberUpdatedState(onBackgroundTap)

    // مواضع الكتل ونتائج قياس النص: لتمرير الصفحة إلى آية معيّنة
    val layouts = remember(page) { mutableStateMapOf<Int, TextLayoutResult>() }
    val tops = remember(page) { mutableStateMapOf<Int, Float>() }
    val topGapPx = with(LocalDensity.current) { 28.dp.toPx() }

    LaunchedEffect(scrollRequest, data) {
        val req = scrollRequest ?: return@LaunchedEffect
        val bi = data.blocks.indexOfFirst { it is PageBlock.AyahRun && it.ayahs.any { a -> a.id == req.ayahId } }
        if (bi < 0) return@LaunchedEffect
        val span = data.formatted[bi]?.second?.firstOrNull { it.ayahId == req.ayahId } ?: return@LaunchedEffect
        val layout = snapshotFlow { layouts[bi] }.filterNotNull().first()
        val top = snapshotFlow { tops[bi] }.filterNotNull().first()
        val line = layout.getLineForOffset(span.start)
        val y = top + layout.getLineTop(line) - topGapPx
        scroll.animateScrollTo(y.roundToInt().coerceAtLeast(0))
    }

    Column(modifier) {
        CenteredCapped { PageHeader(data.info, palette, margin) }
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onTap = { onBg() }) },
        ) {
            val availPx = constraints.maxHeight.toFloat()
            // ملاءمة الصفحة: نكرر قياس ارتفاع المحتوى الفعلي ونعدّل التكبير حتى يستقر (≤ ٦ خطوات)
            var scale by remember(page, prefs.fontSize, prefs.lineSpacing, prefs.pageMargin, fitEnabled) { mutableFloatStateOf(1f) }
            var contentH by remember(page, prefs.fontSize, prefs.lineSpacing, prefs.pageMargin, fitEnabled) { mutableIntStateOf(0) }
            var iteration by remember(page, prefs.fontSize, prefs.lineSpacing, prefs.pageMargin, fitEnabled, availPx) { mutableIntStateOf(0) }
            if (fitEnabled) {
                LaunchedEffect(contentH, availPx) {
                    val step = FitPage.next(scale, contentH.toFloat(), availPx, iteration)
                    iteration = step.iteration
                    if (step.scale != scale) scale = step.scale
                }
            }
            val shown = if (scale == 1f) prefs else prefs.copy(fontSize = prefs.fontSize * scale)

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scroll),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    Modifier
                        .widthIn(max = MAX_CONTENT_WIDTH)
                        .fillMaxWidth()
                        .padding(horizontal = margin)
                        .padding(top = 4.dp, bottom = 24.dp)
                        .onSizeChanged { contentH = it.height },
                ) {
                    PageBody(
                        page = page,
                        quran = quran,
                        data = data,
                        prefs = shown,
                        palette = palette,
                        selectedAyahId = selectedAyahId,
                        focusAyahId = focusAyahId,
                        playingAyahId = playingAyahId,
                        onAyahTap = onAyahTap,
                        onBackgroundTap = onBackgroundTap,
                        onRunLayout = { index, layout -> layouts[index] = layout },
                        onRunTop = { index, y -> tops[index] = y },
                    )
                }
            }
        }
    }
}

/**
 * صفحة داخل التدفق المتواصل (وضع التمرير التلقائي): ترويسة صغيرة ثم جسم الصفحة بلا تمرير خاص بها،
 * فينساب النص من صفحة إلى التي تليها دون قفزات.
 */
@Composable
fun ContinuousPageItem(
    page: Int,
    quran: QuranData,
    prefs: ReaderPrefs,
    palette: ReaderPalette,
    selectedAyahId: Int?,
    focusAyahId: Int?,
    playingAyahId: Int?,
    onAyahTap: (Ayah) -> Unit,
    onBackgroundTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val data = rememberPageData(page, quran)
    val margin = prefs.pageMargin.dp
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .widthIn(max = MAX_CONTENT_WIDTH)
                .fillMaxWidth()
                .padding(horizontal = margin),
        ) {
            Spacer(Modifier.height(10.dp))
            PageHeader(data.info, palette, 0.dp)
            Spacer(Modifier.height(4.dp))
            PageBody(
                page = page,
                quran = quran,
                data = data,
                prefs = prefs,
                palette = palette,
                selectedAyahId = selectedAyahId,
                focusAyahId = focusAyahId,
                playingAyahId = playingAyahId,
                onAyahTap = onAyahTap,
                onBackgroundTap = onBackgroundTap,
                onRunLayout = { _, _ -> },
                onRunTop = { _, _ -> },
            )
        }
    }
}

@Composable
private fun CenteredCapped(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = MAX_CONTENT_WIDTH).fillMaxWidth()) { content() }
    }
}

/** كتل الصفحة (وسوم الأرباع، ترويسات السور، الآيات) ثم رقم الصفحة. مشترك بين وضعي القراءة. */
@Composable
private fun PageBody(
    page: Int,
    quran: QuranData,
    data: PageData,
    prefs: ReaderPrefs,
    palette: ReaderPalette,
    selectedAyahId: Int?,
    focusAyahId: Int?,
    playingAyahId: Int?,
    onAyahTap: (Ayah) -> Unit,
    onBackgroundTap: () -> Unit,
    onRunLayout: (Int, TextLayoutResult) -> Unit,
    onRunTop: (Int, Float) -> Unit,
) {
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
    val style = remember(prefs.fontSize, prefs.lineSpacing, palette) {
        YaqeenTextStyles.quran(prefs.fontSize, prefs.lineSpacing).copy(color = palette.text)
    }
    val onTap by rememberUpdatedState(onAyahTap)
    val onBg by rememberUpdatedState(onBackgroundTap)

    data.blocks.forEachIndexed { index, block ->
        when (block) {
            is PageBlock.HizbMark -> HizbMarkRow(block.label, palette)
            is PageBlock.SurahHeader -> SurahBanner(block.surah, block.showBismillah, quran.bismillah, prefs.fontSize, palette)
            is PageBlock.AyahRun -> AyahRunText(
                formatted = data.formatted[index]!!,
                style = style,
                palette = palette,
                selectedId = selectedAyahId,
                focusId = focusAyahId,
                playingId = playingAyahId,
                bookmarkColors = bookmarkColors,
                ayahs = block.ayahs,
                onTap = { onTap(it) },
                onBackgroundTap = { onBg() },
                onLayout = { onRunLayout(index, it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { onRunTop(index, it.positionInParent().y) },
            )
        }
    }
    PageFooter(page, palette)
}

@Composable
private fun PageHeader(info: PageInfo, palette: ReaderPalette, margin: Dp) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = margin, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "سورة ${QuranText.plainSurahName(info.surah.nameAr)}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "الجزء ${ArabicText.indic(info.juz)} – الحزب ${ArabicText.indic(info.hizb)}",
                style = MaterialTheme.typography.labelMedium,
                color = palette.muted,
                maxLines = 1,
            )
        }
        Box(
            Modifier
                .padding(horizontal = margin)
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.muted.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun PageFooter(page: Int, palette: ReaderPalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(palette.muted.copy(alpha = 0.25f)))
        Text(
            ArabicText.indic(page),
            style = MaterialTheme.typography.labelLarge,
            color = palette.muted,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(palette.muted.copy(alpha = 0.25f)))
    }
}

@Composable
private fun HizbMarkRow(label: String, palette: ReaderPalette) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(palette.accent.copy(alpha = 0.5f)))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = palette.accent,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(palette.accent.copy(alpha = 0.5f)))
    }
}

@Composable
private fun SurahBanner(surah: Surah, showBismillah: Boolean, bismillah: String, fontSize: Float, palette: ReaderPalette) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .border(1.5.dp, palette.accent, RoundedCornerShape(12.dp))
                .background(palette.accent.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .padding(vertical = 10.dp, horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "سُورَةُ ${surah.nameAr}",
                    style = TextStyle(
                        fontFamily = YaqeenFonts.Quran,
                        fontSize = (fontSize * 0.9f).sp,
                        lineHeight = (fontSize * 1.6f).sp,
                        color = palette.text,
                        textAlign = TextAlign.Center,
                    ),
                )
                Text(
                    "${if (surah.meccan) "مكية" else "مدنية"} – ${ArabicText.indic(surah.ayahCount)} آية",
                    style = MaterialTheme.typography.labelSmall,
                    color = palette.muted,
                )
            }
        }
        if (showBismillah) {
            Spacer(Modifier.height(8.dp))
            Text(
                bismillah,
                style = YaqeenTextStyles.quran(fontSize, 1.7f).copy(color = palette.text, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AyahRunText(
    ayahs: List<Ayah>,
    formatted: Pair<String, List<AyahSpan>>,
    style: TextStyle,
    palette: ReaderPalette,
    selectedId: Int?,
    focusId: Int?,
    playingId: Int?,
    bookmarkColors: Map<Int, Color>,
    onTap: (Ayah) -> Unit,
    onBackgroundTap: () -> Unit,
    onLayout: (TextLayoutResult) -> Unit,
    modifier: Modifier = Modifier,
) {
    val (plain, spans) = formatted
    val text = remember(plain, spans, selectedId, focusId, playingId, palette) {
        buildAnnotatedString {
            // كل علامة نهاية آية (رمز واحد في النص) تُستبدل بشارة تُرسم بالكود في مكانها
            var pos = 0
            for (s in spans) {
                append(plain.substring(pos, s.markerStart))
                appendInlineContent(markerId(s.ayahId), QuranText.MARKER_PLACEHOLDER.toString())
                pos = s.end
            }
            append(plain.substring(pos))
            for (s in spans) {
                when (s.ayahId) {
                    playingId -> addStyle(SpanStyle(background = palette.accent.copy(alpha = 0.40f)), s.start, s.end)
                    selectedId, focusId -> addStyle(SpanStyle(background = palette.accent.copy(alpha = 0.25f)), s.start, s.end)
                }
            }
        }
    }
    val badgeSp = style.fontSize.value * 1.2f
    val inline = remember(spans, badgeSp, bookmarkColors, palette) {
        spans.associate { s ->
            markerId(s.ayahId) to InlineTextContent(
                // العرض أكبر من الارتفاع قليلًا: مسافة حول الوردة حتى لا تلامس ذيول الحروف المجاورة
                Placeholder((badgeSp * 1.55f).sp, badgeSp.sp, PlaceholderVerticalAlign.TextCenter),
            ) {
                AyahBadge(s.number, bookmarkColors[s.ayahId] ?: palette.accent)
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tap by rememberUpdatedState(onTap)
    val bgTap by rememberUpdatedState(onBackgroundTap)
    Text(
        text = text,
        style = style,
        inlineContent = inline,
        onTextLayout = {
            layout = it
            onLayout(it)
        },
        modifier = modifier.pointerInput(spans, ayahs) {
            detectTapGestures { pos ->
                val l = layout
                val id = if (l != null) RunFormat.ayahAt(spans, l.getOffsetForPosition(pos)) else null
                val ayah = id?.let { i -> ayahs.firstOrNull { it.id == i } }
                if (ayah != null) tap(ayah) else bgTap()
            }
        },
    )
}

private fun markerId(ayahId: Int) = "m$ayahId"

/**
 * شارة نهاية الآية: وردة مزخرفة بحافة متموجة ودائرة داخلية، يتوسطها رقم الآية بالأرقام الهندية.
 * تُرسم بالكود فتظهر بنفس الشكل على كل الأجهزة (لا تعتمد على تشكيل الخط للرمز U+06DD).
 */
@Composable
private fun AyahBadge(number: Int, color: Color) {
    val digits = ArabicText.indic(number)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) { drawRosette(color) }
        val base = with(LocalDensity.current) { maxHeight.toSp() }
        val factor = if (digits.length >= 3) 0.34f else 0.44f
        val size = base * factor
        Text(
            digits,
            color = color,
            style = TextStyle(
                fontFamily = YaqeenFonts.Ui,
                fontWeight = FontWeight.Medium,
                fontSize = size,
                lineHeight = size,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun DrawScope.drawRosette(color: Color) {
    val c = Offset(size.width / 2f, size.height / 2f)
    val radius = size.minDimension / 2f * 0.95f
    val lobes = 12
    val amp = 0.07f
    val steps = 180
    val path = Path()
    for (i in 0..steps) {
        val t = i.toFloat() / steps * 2f * PI.toFloat()
        val r = radius * (1f - amp + amp * cos(lobes * t))
        val x = c.x + r * cos(t)
        val y = c.y + r * sin(t)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color.copy(alpha = 0.10f))
    drawPath(path, color, style = Stroke(width = radius * 0.07f))
    drawCircle(color.copy(alpha = 0.55f), radius = radius * 0.70f, style = Stroke(width = radius * 0.035f))
}
