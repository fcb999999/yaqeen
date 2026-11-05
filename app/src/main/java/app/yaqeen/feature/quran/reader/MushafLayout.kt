package app.yaqeen.feature.quran.reader

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.runtime.Immutable
import androidx.core.content.res.ResourcesCompat
import app.yaqeen.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * ترتيب صفحات مصحف المدينة كما هو مطبوع: ١٥ سطرًا في كل صفحة، كل سطر بكلماته نفسها
 * (assets/quran/mushaf_lines.json، يبنيه tools/build-mushaf-lines.mjs ويتحقق منه).
 * النص هنا للعرض فقط بترميز خط حفص العثماني؛ النص المتحقَّق منه للبحث والنسخ والمشاركة في QuranData.
 */
@Immutable
class MushafLayout(
    private val pages: List<List<MushafLine>>,
    /** البسملة بترميز خط حفص (لأسطر البسملة). */
    val bismillah: String,
    /** عرض أطول سطر تقريبًا بوحدة em (النسبة المئوية ٩٩): يحدد حجم الخط الموحّد لكل الصفحات. */
    val lineEm: Float,
    /** أصغر مسافة بين كلمتين بوحدة em. */
    val minGapEm: Float,
    /** عرض الكشيدة (ـ) بوحدة em. */
    val tatweelEm: Float,
) {
    val pageCount: Int get() = pages.size
    fun lines(page: Int): List<MushafLine> = pages[(page - 1).coerceIn(0, pages.size - 1)]

    companion object {
        /** عدد أسطر صفحة المصحف. */
        const val LINES = 15
    }
}

sealed interface MushafLine {
    data class Header(val surah: Int) : MushafLine
    data object Bismillah : MushafLine
    data object Empty : MushafLine

    /** سطر آيات: رموزه بالترتيب ومجموع عروضها الطبيعية بوحدة em. */
    class Words(val tokens: List<MushafToken>) : MushafLine {
        val naturalEm: Float = tokens.sumOf { it.em.toDouble() }.toFloat()
    }
}

/** كلمة (أو رقم آية في آخر الآية، أو علامة حزب) مع عرضها الطبيعي بوحدة em. */
@Immutable
class MushafToken(val ayahId: Int, val text: String, val em: Float, val kind: Kind) {
    enum class Kind { Word, AyahEnd, HizbMark }
}

object MushafLayoutLoader {
    const val ASSET_PATH = "quran/mushaf_lines.json"

    /** حجم القياس المرجعي (بكسل): العروض تتناسب طرديًا مع حجم الخط فتُقاس مرة واحدة. */
    private const val REF_PX = 100f

    /** النسبة المئوية لعرض السطر التي يُبنى عليها حجم الخط. */
    private const val LINE_PERCENTILE = 0.95f

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: MushafLayout? = null

    fun quranTypeface(context: Context): Typeface =
        ResourcesCompat.getFont(context.applicationContext, R.font.kfgqpc_hafs) ?: Typeface.DEFAULT

    /** يحمّل الترتيب ويقيس عرض كل كلمة مرة واحدة (خارج الخيط الرئيسي) ويحتفظ به في الذاكرة. */
    suspend fun load(context: Context): MushafLayout {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.Default) {
                val text = context.applicationContext.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(text, quranTypeface(context))
            }.also { cached = it }
        }
    }

    private fun parse(text: String, typeface: Typeface): MushafLayout {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            this.typeface = typeface
            textSize = REF_PX
        }
        val widths = HashMap<String, Float>(20_000)
        fun em(s: String): Float = widths.getOrPut(s) { paint.measureText(s) / REF_PX }

        val root = json.parseToJsonElement(text).jsonObject
        val pages = root.getValue("pages").jsonArray.map { page ->
            page.jsonArray.map { line ->
                when {
                    line is JsonArray -> MushafLine.Words(
                        line.flatMap { seg ->
                            val a = seg.jsonArray
                            val id = a[0].jsonPrimitive.int
                            a[1].jsonPrimitive.content.split(' ').filter { it.isNotEmpty() }.map { t ->
                                val kind = when {
                                    t.all { it in '٠'..'٩' } -> MushafToken.Kind.AyahEnd
                                    t == "۞" -> MushafToken.Kind.HizbMark
                                    else -> MushafToken.Kind.Word
                                }
                                MushafToken(id, t, em(t), kind)
                            }
                        },
                    )
                    else -> {
                        val s = line.jsonPrimitive.content
                        when {
                            s == "B" -> MushafLine.Bismillah
                            s.startsWith("H") -> MushafLine.Header(s.substring(1).toInt())
                            else -> MushafLine.Empty
                        }
                    }
                }
            }
        }
        require(pages.size == 604) { "mushaf pages ${pages.size}" }

        // البسملة: السطر الثاني من الصفحة الأولى (الآية الأولى من الفاتحة) بلا رقمها
        val first = pages[0].firstNotNullOf { it as? MushafLine.Words }
        val bismillah = first.tokens.filter { it.kind == MushafToken.Kind.Word }.joinToString(" ") { it.text }

        val minGapEm = (paint.measureText(" ") / REF_PX).coerceIn(0.12f, 0.35f) * 0.7f
        // عرض السطر الطبيعي (كلمات + أصغر مسافات) لكل أسطر المصحف الكاملة. حجم الخط يُبنى على نسبة مئوية
        // لا على أطول سطر: الأسطر الأطول منها قليلة وتُضغط أفقيًا ضغطًا خفيفًا، والأقصر تُمدّ بالكشيدة كالمطبوع
        val lineWidths = pages.drop(2).flatten().mapNotNull { it as? MushafLine.Words }
            .map { it.naturalEm + minGapEm * (it.tokens.size - 1) }
            .sorted()
        fun pct(q: Float) = lineWidths[(lineWidths.size * q).toInt().coerceAtMost(lineWidths.size - 1)]
        val lineEm = pct(LINE_PERCENTILE)
        android.util.Log.i(
            "MushafLayout",
            "lines=${lineWidths.size} p50=${pct(0.5f)} p75=${pct(0.75f)} p90=${pct(0.9f)} p95=${pct(0.95f)} p99=${pct(0.99f)} max=${lineWidths.last()} minGap=$minGapEm tatweel=${em("ـ")}",
        )
        return MushafLayout(pages, bismillah, lineEm, minGapEm, tatweelEm = em("ـ"))
    }
}
