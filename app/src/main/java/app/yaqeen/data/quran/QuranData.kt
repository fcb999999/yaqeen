package app.yaqeen.data.quran

import android.content.Context
import app.yaqeen.core.ArabicText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * النص القرآني المحلي (للقراءة فقط). المصدر: assets/quran/quran.json المبني بالسكربت tools/build-quran-assets.mjs
 * والمتحقق من عدد السور والآيات وترتيبها ومطابقته لمصدر مستقل. لا يوجد في التطبيق أي كود يعدّل هذا النص.
 */
data class Surah(
    val number: Int,
    /** الاسم العربي بدون كلمة "سورة". */
    val nameAr: String,
    val nameEn: String,
    val meccan: Boolean,
    val ayahCount: Int,
    /** المعرّف العام (1..6236) لأول آية في السورة. */
    val firstAyahId: Int,
) {
    /** السور التي لا تبدأ بالبسملة المنفصلة: التوبة. والفاتحة بسملتها آية. */
    val hasSeparateBismillah: Boolean get() = number != 1 && number != 9
}

data class Ayah(
    /** المعرّف العام 1..6236 (نفسه رقم الآية في خدمات الصوت والتفسير). */
    val id: Int,
    val surah: Int,
    val number: Int,
    val text: String,
    val page: Int,
    val juz: Int,
    val hizbQuarter: Int,
    val sajda: Boolean,
) {
    val hizb: Int get() = (hizbQuarter - 1) / 4 + 1
    /** 0..3 : ربع الحزب داخل الحزب. */
    val quarterInHizb: Int get() = (hizbQuarter - 1) % 4
}

class QuranData(val bismillah: String, val surahs: List<Surah>, val ayahs: List<Ayah>) {
    val totalAyahs: Int get() = ayahs.size
    val pageCount: Int = ayahs.last().page

    private val pageRanges: Array<IntRange> = run {
        val first = IntArray(pageCount + 2) { -1 }
        val last = IntArray(pageCount + 2) { -1 }
        ayahs.forEachIndexed { i, a ->
            if (first[a.page] == -1) first[a.page] = i
            last[a.page] = i
        }
        Array(pageCount + 1) { p -> if (p == 0 || first[p] == -1) IntRange.EMPTY else first[p]..last[p] }
    }

    /** نص مطبَّع لكل آية لأغراض البحث. */
    private val normalized: Array<String> by lazy { Array(ayahs.size) { ArabicText.normalize(ayahs[it].text) } }

    fun surah(number: Int): Surah = surahs[number - 1]
    fun ayah(id: Int): Ayah = ayahs[id - 1]
    fun ayah(surah: Int, number: Int): Ayah = ayahs[surahs[surah - 1].firstAyahId - 1 + number - 1]
    fun ayahsOfPage(page: Int): List<Ayah> = ayahs.slice(pageRanges[page.coerceIn(1, pageCount)])
    fun ayahsOfSurah(number: Int): List<Ayah> {
        val s = surah(number)
        return ayahs.subList(s.firstAyahId - 1, s.firstAyahId - 1 + s.ayahCount)
    }

    fun firstAyahOfPage(page: Int): Ayah = ayahs[pageRanges[page.coerceIn(1, pageCount)].first]
    fun firstAyahOfJuz(juz: Int): Ayah = ayahs.first { it.juz == juz }
    fun firstAyahOfHizbQuarter(hq: Int): Ayah = ayahs.first { it.hizbQuarter == hq }

    /** السور التي تبدأ آيتها الأولى في هذه الصفحة (لعرض ترويسة السورة). */
    fun surahsStartingOnPage(page: Int): List<Surah> =
        ayahsOfPage(page).filter { it.number == 1 }.map { surah(it.surah) }

    /** البحث بالكلمات: كل كلمات الاستعلام يجب أن تظهر في الآية (بعد التطبيع). */
    fun search(query: String, limit: Int = 200): List<Ayah> {
        val terms = ArabicText.normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return emptyList()
        val out = ArrayList<Ayah>()
        for (i in ayahs.indices) {
            val n = normalized[i]
            if (terms.all { n.contains(it) }) {
                out += ayahs[i]
                if (out.size >= limit) break
            }
        }
        return out
    }

    /** مرجع نصي مقروء للآية: "البقرة ٢٥٥". */
    fun label(a: Ayah): String = "${surah(a.surah).nameAr} ${ArabicText.indic(a.number)}"
}

object QuranDataLoader {
    const val ASSET_PATH = "quran/quran.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    @Volatile private var cached: QuranData? = null

    /** يحمّل مرة واحدة من الأصول ويحتفظ بها في الذاكرة. */
    suspend fun load(context: Context): QuranData {
        cached?.let { return it }
        return mutex.withLock {
            cached ?: withContext(Dispatchers.IO) {
                val text = context.applicationContext.assets.open(ASSET_PATH).bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(text)
            }.also { cached = it }
        }
    }

    fun parse(jsonText: String): QuranData {
        val root: JsonObject = json.parseToJsonElement(jsonText).jsonObject
        val surahs = root.getValue("surahs").jsonArray.map {
            val o = it.jsonObject
            Surah(
                number = o.getValue("n").jsonPrimitive.int,
                nameAr = o.getValue("ar").jsonPrimitive.content,
                nameEn = o.getValue("en").jsonPrimitive.content,
                meccan = o.getValue("meccan").jsonPrimitive.boolean,
                ayahCount = o.getValue("count").jsonPrimitive.int,
                firstAyahId = o.getValue("first").jsonPrimitive.int,
            )
        }
        val rows: JsonArray = root.getValue("ayahs").jsonArray
        val ayahs = ArrayList<Ayah>(rows.size)
        rows.forEachIndexed { i, r ->
            val a = r.jsonArray
            ayahs += Ayah(
                id = i + 1,
                surah = a[0].jsonPrimitive.int,
                number = a[1].jsonPrimitive.int,
                text = a[2].jsonPrimitive.content,
                page = a[3].jsonPrimitive.int,
                juz = a[4].jsonPrimitive.int,
                hizbQuarter = a[5].jsonPrimitive.int,
                sajda = a[6].jsonPrimitive.int == 1,
            )
        }
        return QuranData(root.getValue("bismillah").jsonPrimitive.content, surahs, ayahs)
    }
}
