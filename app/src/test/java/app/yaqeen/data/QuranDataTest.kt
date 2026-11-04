package app.yaqeen.data

import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.QuranDataLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * يتحقق من سلامة المصحف المحلي: عدد السور والآيات وترتيبها، الصفحات والأجزاء والأحزاب،
 * وأن الملف لم يُعدَّل عن النسخة المتحقق منها (بصمة SHA-256).
 */
class QuranDataTest {
    companion object {
        // عدد آيات كل سورة (رواية حفص) — مرجع مستقل عن ملف البيانات
        private val EXPECTED = intArrayOf(
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110, 98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60,
            34, 30, 73, 54, 45, 83, 182, 88, 75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13, 14, 11, 11, 18,
            12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36, 25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11,
            11, 8, 3, 9, 5, 4, 7, 3, 6, 3, 5, 4, 5, 6,
        )

        private lateinit var quran: QuranData
        private lateinit var rawBytes: ByteArray

        @JvmStatic
        @BeforeClass
        fun load() {
            val stream = QuranDataTest::class.java.classLoader!!.getResourceAsStream(QuranDataLoader.ASSET_PATH)
                ?: error("quran.json غير موجود في موارد الاختبار")
            rawBytes = stream.readBytes()
            quran = QuranDataLoader.parse(String(rawBytes, Charsets.UTF_8))
        }
    }

    @Test fun surahCountIs114() = assertEquals(114, quran.surahs.size)

    @Test fun totalAyahsIs6236() = assertEquals(6236, quran.totalAyahs)

    @Test fun expectedTableSumsTo6236() = assertEquals(6236, EXPECTED.sum())

    @Test fun everySurahHasTheCorrectAyahCount() {
        quran.surahs.forEachIndexed { i, s ->
            assertEquals("surah ${s.number}", i + 1, s.number)
            assertEquals("ayah count of surah ${s.number}", EXPECTED[i], s.ayahCount)
            assertEquals("stored ayahs of surah ${s.number}", EXPECTED[i], quran.ayahsOfSurah(s.number).size)
        }
    }

    @Test fun ayahNumberingIsContiguousAndOrdered() {
        var id = 0
        for (s in quran.surahs) {
            for ((idx, a) in quran.ayahsOfSurah(s.number).withIndex()) {
                id++
                assertEquals(id, a.id)
                assertEquals(s.number, a.surah)
                assertEquals(idx + 1, a.number)
            }
        }
        assertEquals(6236, id)
    }

    @Test fun pagesJuzAndHizbQuartersAreCompleteAndMonotonic() {
        assertEquals(604, quran.pageCount)
        for (p in 1..604) assertTrue("page $p has ayahs", quran.ayahsOfPage(p).isNotEmpty())
        assertEquals(30, quran.ayahs.maxOf { it.juz })
        assertEquals(240, quran.ayahs.maxOf { it.hizbQuarter })
        assertEquals(1, quran.ayahs.first().page)
        assertEquals(1, quran.ayahs.first().juz)
        quran.ayahs.zipWithNext().forEach { (a, b) ->
            assertTrue(b.page >= a.page && b.page - a.page <= 1)
            assertTrue(b.juz >= a.juz && b.juz - a.juz <= 1)
            assertTrue(b.hizbQuarter >= a.hizbQuarter && b.hizbQuarter - a.hizbQuarter <= 1)
        }
        // مواضع معروفة: بداية الجزء 2 عند البقرة 142 (صفحة 22)، وبداية الجزء 30 عند النبأ
        assertEquals(1, quran.ayah(2, 142).juz - 1)
        assertEquals(22, quran.firstAyahOfJuz(2).page)
        assertEquals(78, quran.firstAyahOfJuz(30).surah)
    }

    @Test fun fifteenSajdaVerses() = assertEquals(15, quran.ayahs.count { it.sajda })

    @Test fun bismillahIsNotDuplicatedInSurahOpenings() {
        // الفاتحة: البسملة آية. التوبة: بلا بسملة. غيرها: تُعرض البسملة منفصلة والآية الأولى بدونها.
        assertEquals(quran.bismillah, quran.ayah(1, 1).text)
        assertFalse(quran.ayah(2, 1).text.startsWith("بِسْمِ"))
        assertEquals("الٓمٓ", quran.ayah(2, 1).text)
        assertFalse(quran.surah(9).hasSeparateBismillah)
        assertFalse(quran.surah(1).hasSeparateBismillah)
        assertTrue(quran.surah(2).hasSeparateBismillah)
        // لا تبدأ أي آية أولى بالبسملة (عدا الفاتحة)
        for (s in quran.surahs.filter { it.number != 1 }) {
            assertFalse("surah ${s.number}", quran.ayah(s.number, 1).text.startsWith("بِسْمِ ٱللَّهِ ٱلرَّحْمَٰنِ"))
        }
    }

    @Test fun knownVersesAreIntact() {
        // المقارنة بالتطبيع لأن ترتيب علامات التشكيل في الحروف المكتوبة يدويًا قد يختلف عن ترميز المصدر
        assertEquals("قل هو الله احد", app.yaqeen.core.ArabicText.normalize(quran.ayah(112, 1).text))
        assertEquals("من الجنه والناس", app.yaqeen.core.ArabicText.normalize(quran.ayah(114, 6).text))
        assertEquals(6236, quran.ayah(114, 6).id)
        assertEquals(604, quran.ayah(114, 6).page)
        assertTrue(app.yaqeen.core.ArabicText.normalize(quran.ayah(2, 255).text).contains("الحي القيوم"))
    }

    @Test fun searchFindsAyahsWithoutDiacritics() {
        val hits = quran.search("الحي القيوم")
        assertTrue(hits.any { it.surah == 2 && it.number == 255 })
        assertTrue(quran.search("").isEmpty())
        assertTrue(quran.search("كلمةلاوجودلهافيالقرآنإطلاقا").isEmpty())
    }

    @Test fun fileMatchesVerifiedFingerprint() {
        val report = File("../tools/data/quran-verification.json")
        if (!report.exists()) return // يُتجاوز إذا شُغّل الاختبار من مكان آخر
        val expected = Regex("\"quran/quran.json\"\\s*:\\s*\"([0-9a-f]{64})\"").find(report.readText())!!.groupValues[1]
        val actual = MessageDigest.getInstance("SHA-256").digest(rawBytes).joinToString("") { "%02x".format(it) }
        assertEquals("quran.json تغيّر عن النسخة المتحقق منها!", expected, actual)
    }
}
