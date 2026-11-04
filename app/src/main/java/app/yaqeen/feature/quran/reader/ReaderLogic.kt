package app.yaqeen.feature.quran.reader

import app.yaqeen.core.ArabicText
import app.yaqeen.data.quran.Ayah
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.Surah

/*
 * منطق خالص (بلا أي اعتماد على Android) لقارئ المصحف: يمكن اختباره على JVM.
 * لا شيء هنا يعدّل النص القرآني؛ كل الدوال تقرأ فقط وتُنسّق العرض.
 */

// ---------------------------------------------------------------- رموز النص والمراجع

object QuranText {
    /** علامة نهاية الآية U+06DD؛ يرسمها خط الأميري القرآني دائرةً تحيط بالرقم الذي يليها. */
    const val AYAH_END_SIGN = '۝'

    /** علامة نهاية الآية + الرقم بالأرقام الهندية: ۝١٢٣ (للنسخ والمشاركة النصية فقط). */
    fun endMarker(number: Int): String = AYAH_END_SIGN + ArabicText.indic(number)

    /**
     * في الصفحة يُستبدل «۝+الرقم» برمز واحد (حرف استبدال الكائن) تُرسم مكانه شارة الآية بالكود،
     * فلا يتوقف ظهور الرقم داخل الإطار على تشكيل خط الجهاز (يختلف بين الإصدارات والأجهزة).
     */
    const val MARKER_PLACEHOLDER = '￼'

    /**
     * للعرض بخط حفص (مجمع الملك فهد) فقط، دون تعديل النص المخزَّن: نص Tanzil يرمز السكون بـ U+0652
     * والصفر المستدير فوق الحرف الزائد بـ U+06DF، أما الخط فيرسم السكون (رأس الخاء) من U+06E1
     * والصفر المستدير من U+0652 كما في مصحف المدينة. التبديل حرف بحرف فلا تتغير المواضع (offsets).
     */
    fun forHafsFont(s: String): String {
        if (s.indexOf('ْ') < 0 && s.indexOf('۟') < 0) return s
        val out = CharArray(s.length)
        for (i in s.indices) {
            out[i] = when (val ch = s[i]) {
                'ْ' -> 'ۡ'
                '۟' -> 'ْ'
                else -> ch
            }
        }
        return String(out)
    }

    private val NAME_MARKS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED\\u0640]")

    /** اسم السورة بلا تشكيل ولا تطويل للمراجع النصية (البقرة، آل عمران). لا يُستعمل مع نص الآيات. */
    fun plainSurahName(nameAr: String): String =
        nameAr.replace("\u0627\u0653", "\u0622").replace(NAME_MARKS, "").replace('\u0671', '\u0627').trim()

    /** مرجع مقروء: [البقرة: ٢٥٥] */
    fun reference(surahNameAr: String, ayahNumber: Int): String =
        "[${plainSurahName(surahNameAr)}: ${ArabicText.indic(ayahNumber)}]"

    /** نص المشاركة/النسخ: «نص الآية» [البقرة: ٢٥٥] — النص كما هو دون أي تعديل. */
    fun shareText(ayahText: String, surahNameAr: String, ayahNumber: Int): String =
        "«$ayahText» ${reference(surahNameAr, ayahNumber)}"
}

// ---------------------------------------------------------------- الأحزاب والأرباع

object HizbLabels {
    fun hizbOf(hizbQuarter: Int): Int = (hizbQuarter - 1) / 4 + 1
    fun juzOfHizb(hizb: Int): Int = (hizb - 1) / 2 + 1

    /** 0..3 : عدد الأرباع التي مضت من الحزب عند بداية هذا الربع. */
    fun quarterIndex(hizbQuarter: Int): Int = (hizbQuarter - 1) % 4

    /** وصف موضع الربع: الحزب ٥ / ربع الحزب ٥ / نصف الحزب ٥ / ثلاثة أرباع الحزب ٥ */
    fun label(hizbQuarter: Int): String {
        val h = ArabicText.indic(hizbOf(hizbQuarter))
        return when (quarterIndex(hizbQuarter)) {
            0 -> "الحزب $h"
            1 -> "ربع الحزب $h"
            2 -> "نصف الحزب $h"
            else -> "ثلاثة أرباع الحزب $h"
        }
    }

    /** وسم يظهر داخل الصفحة عند بداية ربع؛ بداية الجزء تُذكر صراحة. */
    fun markLabel(hizbQuarter: Int): String {
        val hizb = hizbOf(hizbQuarter)
        return if (quarterIndex(hizbQuarter) == 0 && hizb % 2 == 1) {
            "بداية الجزء ${ArabicText.indic(juzOfHizb(hizb))} – ${label(hizbQuarter)}"
        } else label(hizbQuarter)
    }
}

// ---------------------------------------------------------------- تركيب الصفحة

sealed interface PageBlock {
    /** وسم بداية ربع حزب. */
    data class HizbMark(val label: String) : PageBlock

    /** ترويسة سورة تبدأ في هذه الصفحة (والبسملة منفصلة إن وجبت). */
    data class SurahHeader(val surah: Surah, val showBismillah: Boolean) : PageBlock

    /** آيات متتالية تُعرض كنص واحد. */
    data class AyahRun(val ayahs: List<Ayah>) : PageBlock
}

data class PageInfo(val page: Int, val juz: Int, val hizb: Int, val hizbQuarter: Int, val surah: Surah)

object PageLayout {
    /** يفكّ صفحة المصحف (1..604) إلى كتل: ترويسات سور، وسوم أرباع، وآيات. */
    fun blocks(quran: QuranData, page: Int): List<PageBlock> {
        val ayahs = quran.ayahsOfPage(page)
        val out = ArrayList<PageBlock>()
        var run = ArrayList<Ayah>()
        fun flush() {
            if (run.isNotEmpty()) {
                out += PageBlock.AyahRun(run)
                run = ArrayList()
            }
        }
        for (a in ayahs) {
            val prevQuarter = if (a.id > 1) quran.ayah(a.id - 1).hizbQuarter else 0
            val startsQuarter = a.hizbQuarter != prevQuarter
            val startsSurah = a.number == 1
            if (startsQuarter || startsSurah) flush()
            if (startsQuarter) out += PageBlock.HizbMark(HizbLabels.markLabel(a.hizbQuarter))
            if (startsSurah) {
                val s = quran.surah(a.surah)
                out += PageBlock.SurahHeader(s, showBismillah = s.hasSeparateBismillah)
            }
            run += a
        }
        flush()
        return out
    }

    /** معلومات ترويسة الصفحة (من أول آية فيها). */
    fun info(quran: QuranData, page: Int): PageInfo {
        val p = page.coerceIn(1, quran.pageCount)
        val a = quran.firstAyahOfPage(p)
        return PageInfo(p, a.juz, a.hizb, a.hizbQuarter, quran.surah(a.surah))
    }
}

/**
 * موضع آية داخل النص المبني لكتلة آيات: [start, end) ويشمل علامة النهاية (رمز واحد)؛
 * markerStart موضع رمز الشارة؛ number رقم الآية في سورتها.
 */
data class AyahSpan(val ayahId: Int, val start: Int, val markerStart: Int, val end: Int, val number: Int = 0)

object RunFormat {
    /** يبني النص المسطّح لكتلة آيات: «نص ￼» (رمز الشارة) مفصولة بمسافة، ويعيد مواضع كل آية. */
    fun build(ayahs: List<Ayah>): Pair<String, List<AyahSpan>> {
        val sb = StringBuilder()
        val spans = ArrayList<AyahSpan>(ayahs.size)
        ayahs.forEachIndexed { i, a ->
            if (i > 0) sb.append(' ')
            val start = sb.length
            sb.append(a.text).append(' ')
            val markerStart = sb.length
            sb.append(QuranText.MARKER_PLACEHOLDER)
            spans += AyahSpan(a.id, start, markerStart, sb.length, a.number)
        }
        return sb.toString() to spans
    }

    /** الآية التي تحتوي الإزاحة في النص (أو null إن وقعت على الفاصل). */
    fun ayahAt(spans: List<AyahSpan>, offset: Int): Int? =
        spans.firstOrNull { offset >= it.start && offset < it.end }?.ayahId
}

// ---------------------------------------------------------------- الاستئناف عبر الأجهزة

/** نسخة خالصة من سجل القراءة (لكل جهاز سجل واحد). */
data class ProgressPoint(
    val deviceId: String,
    val deviceName: String,
    val surah: Int,
    val ayah: Int,
    val page: Int,
    val juz: Int,
    val lastReadAt: Long,
)

data class ResumeChoice(val point: ProgressPoint, val fromOtherDevice: Boolean)

object Resume {
    /**
     * يختار موضع الاستئناف: أحدث سجل قراءة بين كل الأجهزة. السجلات غير الصالحة تُتجاهَل.
     * عند تساوي الوقت يُفضَّل سجل هذا الجهاز ثم الأكبر معرّفًا (ترتيب حتمي على كل الأجهزة).
     */
    fun select(points: List<ProgressPoint>, ownDeviceId: String, pageCount: Int = 604, surahCount: Int = 114): ResumeChoice? {
        val best = points
            .filter { it.page in 1..pageCount && it.surah in 1..surahCount && it.ayah >= 1 }
            .maxWithOrNull(
                compareBy<ProgressPoint> { it.lastReadAt }
                    .thenBy { if (it.deviceId == ownDeviceId) 1 else 0 }
                    .thenBy { it.deviceId },
            ) ?: return null
        return ResumeChoice(best, fromOtherDevice = best.deviceId != ownDeviceId)
    }

    fun snackbarText(deviceName: String): String {
        val name = deviceName.trim().ifEmpty { "آخر" }
        return "استئناف من آخر موضع على جهاز $name"
    }
}

// ---------------------------------------------------------------- التمرير التلقائي

object AutoScroll {
    const val MIN_SPEED = 1
    const val MAX_SPEED = 10

    data class Preset(val label: String, val speed: Int)

    val presets = listOf(
        Preset("بطيء", 2),
        Preset("متوسط", 4),
        Preset("سريع", 6),
        Preset("أسرع", 9),
    )

    fun clampSpeed(speed: Int): Int = speed.coerceIn(MIN_SPEED, MAX_SPEED)

    /** اسم المستوى الحالي للعرض. */
    fun levelName(speed: Int): String = when (clampSpeed(speed)) {
        in 1..2 -> "بطيء"
        in 3..4 -> "متوسط"
        in 5..7 -> "سريع"
        else -> "أسرع"
    }

    /** سرعة التمرير بوحدة dp/ث: من ١٥٫٥ (المستوى ١) إلى ٨٣ (المستوى ١٠). */
    fun dpPerSecond(speed: Int): Float = 8f + 7.5f * clampSpeed(speed)
}

// ---------------------------------------------------------------- ملاءمة الصفحة للشاشة

/** خطوة من حساب تكبير الخط ليملأ نص الصفحة ارتفاع الشاشة (في الأجهزة اللوحية خصوصًا). */
data class FitStep(val scale: Float, val iteration: Int, val done: Boolean)

object FitPage {
    const val MAX_SCALE = 1.6f

    /** نملأ هذه النسبة من الارتفاع فقط حتى لا يظهر تمرير طفيف بسبب فروق التقريب. */
    const val FILL = 0.96f
    const val MAX_ITERATIONS = 6
    private const val TOLERANCE = 0.025f

    /**
     * يُستدعى كلما قيس ارتفاع المحتوى الفعلي [measured] عند التكبير الحالي [scale] ضمن ارتفاع متاح [available].
     * الارتفاع يتناسب تقريبًا مع مربع التكبير (عدد الأسطر × ارتفاع السطر)، فنكرر التقريب حتى يستقر.
     * لا نصغّر أبدًا عن حجم الخط الذي اختاره المستخدم (scale ≥ 1)، وإن كان المحتوى أطول من الشاشة بقي كما هو ويُمرَّر.
     */
    fun next(scale: Float, measured: Float, available: Float, iteration: Int, maxScale: Float = MAX_SCALE): FitStep {
        if (measured <= 0f || available <= 0f) return FitStep(scale, iteration, done = false)
        val target = available * FILL
        val error = (target - measured) / target
        if (kotlin.math.abs(error) <= TOLERANCE) return FitStep(scale, iteration, done = true)
        val wanted = (scale * kotlin.math.sqrt(target / measured)).coerceIn(1f, maxScale)
        if (kotlin.math.abs(wanted - scale) < 0.005f || iteration >= MAX_ITERATIONS) {
            return FitStep(if (measured > available) wanted else scale, iteration, done = true)
        }
        return FitStep(wanted, iteration + 1, done = false)
    }
}

// ---------------------------------------------------------------- القفز إلى موضع

object JumpParser {
    private val INDIC = '٠'..'٩'
    private val PERSIAN = '۰'..'۹'

    /** يحوّل الأرقام الهندية/الفارسية إلى لاتينية ويحذف ما ليس رقمًا. */
    fun digits(input: String): String = buildString {
        for (ch in input) when (ch) {
            in '0'..'9' -> append(ch)
            in INDIC -> append('0' + (ch - '٠'))
            in PERSIAN -> append('0' + (ch - '۰'))
        }
    }

    private fun bounded(input: String, max: Int): Int? =
        digits(input).takeIf { it.isNotEmpty() && it.length <= 5 }?.toIntOrNull()?.takeIf { it in 1..max }

    fun ayahNumber(input: String, ayahCount: Int): Int? = bounded(input, ayahCount)
    fun page(input: String, pageCount: Int = 604): Int? = bounded(input, pageCount)
    fun juz(input: String, juzCount: Int = 30): Int? = bounded(input, juzCount)
}

// ---------------------------------------------------------------- الفهرس

data class QuarterEntry(val hizbQuarter: Int, val first: Ayah)

object IndexFilter {
    /** أول آية في كل ربع (240 عنصرًا). */
    fun quarters(quran: QuranData): List<QuarterEntry> {
        val out = ArrayList<QuarterEntry>(240)
        var last = 0
        for (a in quran.ayahs) if (a.hizbQuarter != last) {
            out += QuarterEntry(a.hizbQuarter, a)
            last = a.hizbQuarter
        }
        return out
    }

    /** أول آية في كل جزء (30 عنصرًا). */
    fun juzStarts(quran: QuranData): List<Ayah> {
        val out = ArrayList<Ayah>(30)
        var last = 0
        for (a in quran.ayahs) if (a.juz != last) {
            out += a
            last = a.juz
        }
        return out
    }

    private fun norm(s: String) = ArabicText.normalize(s.replace("سورة", "").replace("سُورَةُ", ""))

    private fun surahMatches(s: Surah, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        val num = JumpParser.digits(q)
        if (num.isNotEmpty() && num.length == q.filter { it.isLetterOrDigit() }.length) {
            return s.number.toString().startsWith(num)
        }
        val nq = norm(q)
        if (nq.isEmpty()) return true
        return norm(s.nameAr).contains(nq) || s.nameEn.lowercase().contains(q.lowercase())
    }

    fun surahs(quran: QuranData, query: String): List<Surah> = quran.surahs.filter { surahMatches(it, query) }

    fun juz(quran: QuranData, starts: List<Ayah>, query: String): List<Ayah> {
        val q = query.trim()
        if (q.isEmpty()) return starts
        val num = JumpParser.digits(q)
        if (num.isNotEmpty()) return starts.filter { it.juz.toString().startsWith(num) }
        val nq = norm(q)
        return starts.filter { norm(quran.surah(it.surah).nameAr).contains(nq) || "جزء".contains(nq) }
    }

    fun quarters(quran: QuranData, all: List<QuarterEntry>, query: String): List<QuarterEntry> {
        val q = query.trim()
        if (q.isEmpty()) return all
        val num = JumpParser.digits(q)
        if (num.isNotEmpty()) return all.filter { HizbLabels.hizbOf(it.hizbQuarter).toString() == num }
        val nq = norm(q)
        return all.filter {
            ArabicText.normalize(HizbLabels.label(it.hizbQuarter)).contains(nq) ||
                norm(quran.surah(it.first.surah).nameAr).contains(nq)
        }
    }
}

// ---------------------------------------------------------------- إبراز كلمات البحث

object SearchHighlight {
    private fun isMark(c: Char) = c != ' ' && ArabicText.normalize(c.toString()).isEmpty()

    /**
     * مواضع الكلمات المطابقة داخل النص الأصلي (بتشكيله): البحث يتم على نسخة مطبَّعة ثم تُحوَّل المواضع
     * إلى النص الأصلي، ويشمل المدى علامات التشكيل التي تلي آخر حرف مطابق.
     */
    fun ranges(original: String, query: String): List<IntRange> {
        val terms = ArabicText.normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty() || original.isEmpty()) return emptyList()
        val norm = StringBuilder()
        val origIndex = ArrayList<Int>() // موضع كل حرف مطبَّع في النص الأصلي
        for (i in original.indices) {
            val ch = original[i]
            if (ch.isWhitespace()) {
                if (norm.isNotEmpty() && norm.last() != ' ') { norm.append(' '); origIndex += i }
                continue
            }
            val n = ArabicText.normalize(ch.toString())
            for (nc in n) { norm.append(nc); origIndex += i }
        }
        val found = ArrayList<IntRange>()
        for (t in terms) {
            var from = 0
            while (true) {
                val at = norm.indexOf(t, from)
                if (at < 0) break
                val s = origIndex[at]
                var e = origIndex[at + t.length - 1] + 1
                while (e < original.length && isMark(original[e])) e++
                found += s until e
                from = at + t.length
            }
        }
        // دمج المتداخل
        val sorted = found.sortedBy { it.first }
        val merged = ArrayList<IntRange>()
        for (r in sorted) {
            val last = merged.lastOrNull()
            if (last != null && r.first <= last.last + 1) {
                merged[merged.size - 1] = last.first..maxOf(last.last, r.last)
            } else merged += r
        }
        return merged
    }
}

// ---------------------------------------------------------------- الأوراد

object PlanMath {
    fun total(startAyahId: Int, endAyahId: Int): Int = (endAyahId - startAyahId + 1).coerceAtLeast(0)

    /** عدد الآيات المنجزة: progressAyahId = آخر آية أُنجزت (startAyahId-1 عند البداية). */
    fun done(startAyahId: Int, endAyahId: Int, progressAyahId: Int): Int =
        (progressAyahId - startAyahId + 1).coerceIn(0, total(startAyahId, endAyahId))

    fun fraction(startAyahId: Int, endAyahId: Int, progressAyahId: Int): Float {
        val t = total(startAyahId, endAyahId)
        return if (t == 0) 0f else done(startAyahId, endAyahId, progressAyahId).toFloat() / t
    }
}
