package app.yaqeen.feature.quran.reader

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlin.math.roundToInt

/** طلب تمرير داخل الصفحة إلى آية (nonce يجعل الطلب المتكرر لنفس الآية يُنفَّذ من جديد). */
data class ScrollRequest(val ayahId: Int, val nonce: Int)

/**
 * صفحة واحدة من المصحف (1..604): ترويسة خفيفة (السورة/الجزء/الحزب) ثم نص الآيات بخط المصحف
 * مع ترويسات السور والبسملة ووسوم الأرباع وعلامة نهاية كل آية ورقم الصفحة.
 * النص كما هو في البيانات دون أي تعديل.
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
) {
    val c = LocalAppContainer.current
    val blocks = remember(page, quran) { PageLayout.blocks(quran, page) }
    val info = remember(page, quran) { PageLayout.info(quran, page) }
    val formatted = remember(blocks) { blocks.map { (it as? PageBlock.AyahRun)?.let { r -> RunFormat.build(r.ayahs) } } }
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
    val margin = prefs.pageMargin.dp
    val onTap by rememberUpdatedState(onAyahTap)
    val onBg by rememberUpdatedState(onBackgroundTap)

    // مواضع الكتل ونتائج قياس النص: لتمرير الصفحة إلى آية معيّنة
    val layouts = remember(page) { mutableStateMapOf<Int, TextLayoutResult>() }
    val tops = remember(page) { mutableStateMapOf<Int, Float>() }
    val topGapPx = with(LocalDensity.current) { 28.dp.toPx() }

    LaunchedEffect(scrollRequest, blocks) {
        val req = scrollRequest ?: return@LaunchedEffect
        val bi = blocks.indexOfFirst { it is PageBlock.AyahRun && it.ayahs.any { a -> a.id == req.ayahId } }
        if (bi < 0) return@LaunchedEffect
        val span = formatted[bi]?.second?.firstOrNull { it.ayahId == req.ayahId } ?: return@LaunchedEffect
        val layout = snapshotFlow { layouts[bi] }.filterNotNull().first()
        val top = snapshotFlow { tops[bi] }.filterNotNull().first()
        val line = layout.getLineForOffset(span.start)
        val y = top + layout.getLineTop(line) - topGapPx
        scroll.animateScrollTo(y.roundToInt().coerceAtLeast(0))
    }

    Column(modifier) {
        PageHeader(info, palette, margin)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures(onTap = { onBg() }) },
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = margin)
                    .padding(top = 4.dp, bottom = 24.dp),
            ) {
                blocks.forEachIndexed { index, block ->
                    when (block) {
                        is PageBlock.HizbMark -> HizbMarkRow(block.label, palette)
                        is PageBlock.SurahHeader -> SurahBanner(block.surah, block.showBismillah, quran.bismillah, prefs.fontSize, palette)
                        is PageBlock.AyahRun -> AyahRunText(
                            formatted = formatted[index]!!,
                            style = style,
                            palette = palette,
                            selectedId = selectedAyahId,
                            focusId = focusAyahId,
                            playingId = playingAyahId,
                            bookmarkColors = bookmarkColors,
                            ayahs = block.ayahs,
                            onTap = { onTap(it) },
                            onBackgroundTap = { onBg() },
                            onLayout = { layouts[index] = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { tops[index] = it.positionInParent().y },
                        )
                    }
                }
                PageFooter(page, palette)
            }
        }
    }
}

@Composable
private fun PageHeader(info: PageInfo, palette: ReaderPalette, margin: androidx.compose.ui.unit.Dp) {
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
    val text = remember(plain, spans, selectedId, focusId, playingId, bookmarkColors, palette) {
        buildAnnotatedString {
            append(plain)
            for (s in spans) {
                // علامة نهاية الآية بلون الهوية (أو لون علامة المستخدم المرجعية)
                addStyle(SpanStyle(color = bookmarkColors[s.ayahId] ?: palette.accent), s.markerStart, s.end)
                when (s.ayahId) {
                    playingId -> addStyle(SpanStyle(background = palette.accent.copy(alpha = 0.40f)), s.start, s.end)
                    selectedId, focusId -> addStyle(SpanStyle(background = palette.accent.copy(alpha = 0.25f)), s.start, s.end)
                }
            }
        }
    }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val tap by rememberUpdatedState(onTap)
    val bgTap by rememberUpdatedState(onBackgroundTap)
    Text(
        text = text,
        style = style,
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
