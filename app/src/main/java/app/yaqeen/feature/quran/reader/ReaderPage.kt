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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.draw.drawWithContent
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
import app.yaqeen.ui.theme.YaqeenTheme
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

    val noor = YaqeenTheme.isNoor
    Column(
        modifier.drawBehind {
            // إطار رفيع حول الصفحة داخل الهامش كحدّ الورقة المطبوعة (لا يأخذ من عرض النص شيئًا)؛ «نور» بلا إطار كما في صوره
            if (noor) return@drawBehind
            val inset = 3.dp.toPx()
            drawRoundRect(
                palette.accent.copy(alpha = 0.28f),
                topLeft = Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx()),
                style = Stroke(1.dp.toPx()),
            )
        },
    ) {
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
    if (YaqeenTheme.isNoor) {
        NoorPageHeader(info, palette, margin)
        return
    }
    // ترويسة مزخرفة: اسم السورة والجزء/الحزب في إطارين مدوّرين تصل بينهما خطوط ذهبية رفيعة
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = margin, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderPill("سورة ${QuranText.plainSurahName(info.surah.nameAr)}", palette, Modifier.weight(1f, fill = false))
        Box(Modifier.weight(1f).padding(horizontal = 8.dp).height(1.dp).background(palette.accent.copy(alpha = 0.35f)))
        HeaderPill("الجزء ${ArabicText.indic(info.juz)} – الحزب ${ArabicText.indic(info.hizb)}", palette)
    }
}

@Composable
private fun HeaderPill(label: String, palette: ReaderPalette, modifier: Modifier = Modifier) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
        color = palette.accent,
        modifier = modifier
            .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 3.dp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun PageFooter(page: Int, palette: ReaderPalette) {
    if (YaqeenTheme.isNoor) {
        NoorPageFooter(page, palette)
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(palette.accent.copy(alpha = 0.35f)))
        // رقم الصفحة داخل وردة مزخرفة كما في المصاحف المطبوعة
        Box(Modifier.padding(horizontal = 10.dp).size(40.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) { drawRosette(palette.accent, center, size.minDimension / 2f * 0.94f) }
            Text(
                ArabicText.indic(page),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = palette.accent,
                maxLines = 1,
            )
        }
        Box(Modifier.weight(1f).height(1.dp).background(palette.accent.copy(alpha = 0.35f)))
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
        Box(Modifier.weight(1f).height(1.dp).background(palette.accent.copy(alpha = 0.45f)))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = palette.accent,
            modifier = Modifier
                .padding(horizontal = 8.dp)
                .background(palette.accent.copy(alpha = 0.08f), RoundedCornerShape(50))
                .border(1.dp, palette.accent.copy(alpha = 0.55f), RoundedCornerShape(50))
                .padding(horizontal = 14.dp, vertical = 3.dp),
        )
        Box(Modifier.weight(1f).height(1.dp).background(palette.accent.copy(alpha = 0.45f)))
    }
}

@Composable
private fun SurahBanner(surah: Surah, showBismillah: Boolean, bismillah: String, fontSize: Float, palette: ReaderPalette) {
    if (YaqeenTheme.isNoor) {
        NoorSurahBanner(surah, showBismillah, bismillah, fontSize, palette)
        return
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(palette.accent.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .drawBehind {
                    // إطار مزدوج (خارجي سميك وداخلي رفيع) ووردتان عند الطرفين كترويسة السور في المصحف المطبوع
                    val outer = 1.6.dp.toPx()
                    val gap = 5.dp.toPx()
                    val corner = 14.dp.toPx()
                    drawRoundRect(
                        palette.accent,
                        topLeft = Offset(outer / 2f, outer / 2f),
                        size = androidx.compose.ui.geometry.Size(size.width - outer, size.height - outer),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner),
                        style = Stroke(outer),
                    )
                    val inset = outer + gap
                    drawRoundRect(
                        palette.accent.copy(alpha = 0.6f),
                        topLeft = Offset(inset, inset),
                        size = androidx.compose.ui.geometry.Size(size.width - inset * 2f, size.height - inset * 2f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner - 3.dp.toPx()),
                        style = Stroke(0.8.dp.toPx()),
                    )
                    val r = 11.dp.toPx()
                    val edge = inset + r + 6.dp.toPx()
                    drawRosette(palette.accent, Offset(edge, size.height / 2f), r)
                    drawRosette(palette.accent, Offset(size.width - edge, size.height / 2f), r)
                }
                .padding(vertical = 14.dp, horizontal = 44.dp),
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

/** قطر شارة الآية بالنسبة لحجم الخط (٣ خانات تُكبَّر قليلًا لتبقى الأرقام مقروءة). */
private const val BADGE_EM = 1.15f
private const val BADGE_EM_WIDE = 1.35f

/** مركز الشارة فوق خط الأساس بهذا الكسر من حجم الخط (مركز جسم الحروف العربية لا مركز السطر). */
private const val BADGE_ABOVE_BASELINE = 0.24f

/** ذيل صغير بعد الوردة (جهة نهاية السطر)، ومسافة أمان قبلها (جهة الكلمة السابقة) تفوق تجاوز ذيول خط المصحف. */
private const val BADGE_TAIL_EM = 0.08f
private const val BADGE_CLEARANCE_EM = 0.62f

private fun badgeEm(number: Int) = if (number >= 100) BADGE_EM_WIDE else BADGE_EM

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
            // علامة نهاية كل آية رمز واحد في النص يحجز مكان الشارة (فيعمل التباعد المضبوط للأسطر على الكلمات والشارة معًا)
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
    val fontSp = style.fontSize.value
    // الفراغ المحجوز فقط (محتواه فارغ): الشارة نفسها تُرسم فوق النص من موضع الرمز الفعلي بعد التخطيط؛
    // لأن موضع المحتوى المضمَّن لا يراعي تباعد المحاذاة المضبوطة (Justify) فكان يتراكب مع الكلمة السابقة.
    val inline = remember(spans, fontSp) {
        spans.associate { s ->
            val side = fontSp * badgeEm(s.number)
            markerId(s.ayahId) to InlineTextContent(
                Placeholder((side + fontSp * (BADGE_TAIL_EM + BADGE_CLEARANCE_EM)).sp, side.sp, PlaceholderVerticalAlign.TextCenter),
            ) { }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tap by rememberUpdatedState(onTap)
    val bgTap by rememberUpdatedState(onBackgroundTap)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val fontPx = with(density) { style.fontSize.toPx() }
    // أرقام الشارات تُقاس مرة واحدة لكل صفحة (لا في كل إطار رسم، فالتمرير التلقائي يرسم ٦٠ إطارًا في الثانية)
    val digitLayouts = remember(spans, fontPx, measurer, density) {
        spans.associate { s -> s.ayahId to measureBadgeDigits(measurer, density, s.number, fontPx * badgeEm(s.number)) }
    }
    Text(
        text = text,
        style = style,
        inlineContent = inline,
        onTextLayout = {
            layout = it
            onLayout(it)
        },
        modifier = modifier
            .pointerInput(spans, ayahs) {
                detectTapGestures { pos ->
                    val l = layout
                    val id = if (l != null) RunFormat.ayahAt(spans, l.getOffsetForPosition(pos)) else null
                    val ayah = id?.let { i -> ayahs.firstOrNull { it.id == i } }
                    if (ayah != null) tap(ayah) else bgTap()
                }
            }
            .drawWithContent {
                // الشارة تُرسم خلف النص: ذيول الحروف تتجاوز عرض الكلمة المقيس فلا تغطيها الشارة أبدًا
                val l = layout
                if (l != null) {
                    for (s in spans) {
                        val box = runCatching { l.getBoundingBox(s.markerStart) }.getOrNull() ?: continue
                        val line = l.getLineForOffset(s.markerStart)
                        val d = fontPx * badgeEm(s.number)
                        // الوردة ملاصقة لطرف الصندوق البعيد عن الكلمة السابقة، والباقي مسافة أمان
                        val center = Offset(box.left + fontPx * BADGE_TAIL_EM + d / 2f, l.getLineBaseline(line) - fontPx * BADGE_ABOVE_BASELINE)
                        val digits = digitLayouts[s.ayahId] ?: continue
                        drawBadge(digits, bookmarkColors[s.ayahId] ?: palette.accent, center, d)
                    }
                }
                drawContent()
            },
    )
}

private fun markerId(ayahId: Int) = "m$ayahId"

/**
 * شارة نهاية الآية: وردة مزخرفة بحافة متموجة ودائرة داخلية، يتوسطها رقم الآية بالأرقام الهندية.
 * تُرسم بالكود فتظهر بنفس الشكل على كل الأجهزة (لا تعتمد على تشكيل الخط للرمز U+06DD)،
 * ويُصغَّر الرقم تلقائيًا حتى يتّسع داخل الدائرة الداخلية مهما كان عدد خاناته.
 */
/** يقيس رقم الآية بحجم يتّسع داخل الدائرة الداخلية للوردة (يُصغَّر للأرقام الثلاثية). */
private fun measureBadgeDigits(measurer: TextMeasurer, density: androidx.compose.ui.unit.Density, number: Int, diameter: Float): TextLayoutResult {
    val digits = ArabicText.indic(number)
    val maxWidth = diameter * 0.62f
    fun measure(px: Float) = measurer.measure(
        digits,
        style = TextStyle(
            fontFamily = YaqeenFonts.Ui,
            fontWeight = FontWeight.Medium,
            fontSize = with(density) { px.toSp() },
            lineHeight = with(density) { px.toSp() },
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
        maxLines = 1,
        softWrap = false,
    )
    var px = diameter * 0.46f
    var m = measure(px)
    if (m.size.width > maxWidth) {
        px *= maxWidth / m.size.width
        m = measure(px)
    }
    return m
}

private fun DrawScope.drawBadge(digits: TextLayoutResult, color: Color, center: Offset, diameter: Float) {
    drawRosette(color, center, diameter / 2f * 0.95f)
    val px = digits.layoutInput.style.fontSize.toPx()
    drawText(
        digits,
        color = color,
        topLeft = Offset(center.x - digits.size.width / 2f, center.y + 0.34f * px - digits.firstBaseline),
    )
}

private fun DrawScope.drawRosette(color: Color, c: Offset, radius: Float) {
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
    drawCircle(color.copy(alpha = 0.55f), center = c, radius = radius * 0.70f, style = Stroke(width = radius * 0.035f))
}

// ---------------------------------------------------------------- زخارف نمط «نور»
//
// مستوحاة من المصاحف الرقمية المطبوعة: ترويسة واحدة بطرفين معيّنين، وإطار سورة بحليتين جانبيتين،
// ورقم الصفحة في كبسولة صغيرة. كلها تُرسم بالكود بلون الصفحة المختارة (كريمي/أخضر/أزرق/كحلي...).

private val JUZ_ORDINALS = listOf(
    "الأول", "الثاني", "الثالث", "الرابع", "الخامس", "السادس", "السابع", "الثامن", "التاسع", "العاشر",
    "الحادي عشر", "الثاني عشر", "الثالث عشر", "الرابع عشر", "الخامس عشر", "السادس عشر", "السابع عشر",
    "الثامن عشر", "التاسع عشر", "العشرون", "الحادي والعشرون", "الثاني والعشرون", "الثالث والعشرون",
    "الرابع والعشرون", "الخامس والعشرون", "السادس والعشرون", "السابع والعشرون", "الثامن والعشرون",
    "التاسع والعشرون", "الثلاثون",
)

private fun juzName(juz: Int) = "الجزء ${JUZ_ORDINALS.getOrElse(juz - 1) { ArabicText.indic(juz) }}"

private fun noorLabelStyle(palette: ReaderPalette) = TextStyle(
    fontFamily = YaqeenFonts.Quran,
    fontSize = 15.sp,
    lineHeight = 22.sp,
    color = palette.text.copy(alpha = 0.85f),
)

/** كبسولة بطرفين معيّنين: جسم مدوّر بإطار مزدوج، ومعيّن مزخرف عند كل طرف. */
@Composable
private fun NoorCartouche(
    palette: ReaderPalette,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    Row(
        modifier
            .drawBehind { drawCartouche(palette) }
            .padding(horizontal = 30.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

private fun DrawScope.drawCartouche(p: ReaderPalette) {
    val h = size.height
    val w = size.width
    val cap = 24.dp.toPx()
    val stroke = 1.dp.toPx()
    val r = androidx.compose.ui.geometry.CornerRadius(h * 0.32f)
    // الجسم
    drawRoundRect(p.accent.copy(alpha = 0.10f), topLeft = Offset(cap, 0f), size = androidx.compose.ui.geometry.Size(w - cap * 2f, h), cornerRadius = r)
    drawRoundRect(p.accent.copy(alpha = 0.75f), topLeft = Offset(cap, 0f), size = androidx.compose.ui.geometry.Size(w - cap * 2f, h), cornerRadius = r, style = Stroke(stroke))
    val inset = 3.dp.toPx()
    drawRoundRect(
        p.accent.copy(alpha = 0.35f),
        topLeft = Offset(cap + inset, inset),
        size = androidx.compose.ui.geometry.Size(w - cap * 2f - inset * 2f, h - inset * 2f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.25f),
        style = Stroke(stroke * 0.7f),
    )
    // الطرفان المعيّنان
    for (cx in listOf(cap * 0.5f, w - cap * 0.5f)) {
        val hw = cap * 0.46f
        val hh = h * 0.36f
        val diamond = Path().apply {
            moveTo(cx, h / 2f - hh); lineTo(cx + hw, h / 2f); lineTo(cx, h / 2f + hh); lineTo(cx - hw, h / 2f); close()
        }
        drawPath(diamond, p.bg)
        drawPath(diamond, p.accent.copy(alpha = 0.85f), style = Stroke(stroke))
        drawRosette(p.accent, Offset(cx, h / 2f), hh * 0.42f)
        // وصلة قصيرة بين المعيّن والجسم
        val toward = if (cx < w / 2f) cx + hw else cx - hw
        val bodyEdge = if (cx < w / 2f) cap else w - cap
        drawLine(p.accent.copy(alpha = 0.6f), Offset(toward, h / 2f), Offset(bodyEdge, h / 2f), strokeWidth = stroke)
    }
}

@Composable
private fun NoorPageHeader(info: PageInfo, palette: ReaderPalette, margin: Dp) {
    NoorCartouche(palette, Modifier.fillMaxWidth().padding(horizontal = margin, vertical = 8.dp)) {
        Text(
            "${juzName(info.juz)} | الحزب ${ArabicText.indic(info.hizb)}",
            style = noorLabelStyle(palette),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.weight(1f))
        Text("سُورَةُ ${info.surah.nameAr}", style = noorLabelStyle(palette), maxLines = 1)
    }
}

@Composable
private fun NoorPageFooter(page: Int, palette: ReaderPalette) {
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
        NoorCartouche(palette) {
            Text(
                ArabicText.indic(page),
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium),
                color = palette.text.copy(alpha = 0.8f),
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

/** إطار السورة: مستطيل بإطار مزدوج، وحليتان جانبيتان بورود، ووسط بشكل سداسي مدبّب يحمل اسم السورة. */
@Composable
private fun NoorSurahBanner(surah: Surah, showBismillah: Boolean, bismillah: String, fontSize: Float, palette: ReaderPalette) {
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((fontSize * 2.2f).coerceIn(56f, 96f).dp)
                .drawBehind { drawNoorBanner(palette) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "سُورَةُ ${surah.nameAr}",
                style = TextStyle(
                    fontFamily = YaqeenFonts.Quran,
                    fontSize = (fontSize * 0.85f).sp,
                    lineHeight = (fontSize * 1.3f).sp,
                    color = palette.text,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
        if (showBismillah) {
            Spacer(Modifier.height(10.dp))
            Text(
                bismillah,
                style = YaqeenTextStyles.quran(fontSize, 1.7f).copy(color = palette.text, textAlign = TextAlign.Center),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

private fun DrawScope.drawNoorBanner(p: ReaderPalette) {
    val w = size.width
    val h = size.height
    val s = 1.2.dp.toPx()
    val corner = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
    drawRoundRect(p.accent.copy(alpha = 0.12f), cornerRadius = corner)
    drawRoundRect(p.accent.copy(alpha = 0.9f), cornerRadius = corner, style = Stroke(s * 1.3f))
    val i = 4.dp.toPx()
    drawRoundRect(
        p.accent.copy(alpha = 0.5f),
        topLeft = Offset(i, i),
        size = androidx.compose.ui.geometry.Size(w - i * 2f, h - i * 2f),
        cornerRadius = corner,
        style = Stroke(s * 0.7f),
    )
    // الحليتان الجانبيتان
    val panel = w * 0.24f
    for (left in listOf(true, false)) {
        val x0 = if (left) i * 2f else w - i * 2f - panel
        drawRect(p.accent.copy(alpha = 0.16f), topLeft = Offset(x0, i * 2f), size = androidx.compose.ui.geometry.Size(panel, h - i * 4f))
        val cy = h / 2f
        val rr = (h - i * 4f) * 0.26f
        drawRosette(p.accent, Offset(x0 + panel * 0.28f, cy), rr)
        drawRosette(p.accent, Offset(x0 + panel * 0.72f, cy), rr)
        val dx = x0 + panel * 0.5f
        val dh = rr * 0.9f
        val diamond = Path().apply { moveTo(dx, cy - dh); lineTo(dx + dh * 0.7f, cy); lineTo(dx, cy + dh); lineTo(dx - dh * 0.7f, cy); close() }
        drawPath(diamond, p.accent.copy(alpha = 0.7f))
    }
    // الوسط: سداسي مدبّب الطرفين
    val x1 = i * 2f + panel + 6.dp.toPx()
    val x2 = w - i * 2f - panel - 6.dp.toPx()
    val top = h * 0.16f
    val bottom = h * 0.84f
    val tip = (bottom - top) * 0.5f
    val hex = Path().apply {
        moveTo(x1, h / 2f); lineTo(x1 + tip, top); lineTo(x2 - tip, top); lineTo(x2, h / 2f); lineTo(x2 - tip, bottom); lineTo(x1 + tip, bottom); close()
    }
    drawPath(hex, p.bg)
    drawPath(hex, p.accent.copy(alpha = 0.85f), style = Stroke(s))
}
