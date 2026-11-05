package app.yaqeen.feature.quran.reader

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MushafTest {

    // ---------------- الكشيدة

    @Test
    fun kashidaGoesInLastJoinAfterMarks() {
        // ٱلنَّاسِ: آخر وصلة صالحة قبل الألف، بعد علامات النون (الشدة والفتحة)
        val w = "ٱلنَّاسِ"
        val s = Kashida.stretch(w, 1)
        assertEquals("ٱلنَّـاسِ", s)
        // الكشيدة لا تحذف حرفًا ولا حركة
        assertEquals(w, s.replace("ـ", ""))
    }

    @Test
    fun kashidaRepeats() {
        assertEquals("مِـــنَ", Kashida.stretch("مِنَ", 3))
    }

    @Test
    fun noKashidaInAllahOrLamAlefOrUnjoinable() {
        assertEquals(-1, Kashida.position("ٱللَّهُ"))
        assertEquals(-1, Kashida.position("لِلَّهِ"))
        assertEquals(-1, Kashida.position("لَا"))          // لام ألف
        assertEquals(-1, Kashida.position("وَرَدَ"))        // حروف لا تتصل بما بعدها
        assertEquals(-1, Kashida.position("١٤٢"))
        assertEquals(-1, Kashida.position("۞"))
    }

    // ---------------- ملف أسطر المصحف

    @Test
    fun mushafLinesAssetIsComplete() {
        val stream = MushafTest::class.java.classLoader!!.getResourceAsStream(MushafLayoutLoader.ASSET_PATH)
        val root = Json.parseToJsonElement(stream.bufferedReader(Charsets.UTF_8).use { it.readText() }).jsonObject
        val pages = root.getValue("pages").jsonArray
        assertEquals(604, pages.size)
        var nextAyah = 1
        var headers = 0
        var bismillahs = 0
        pages.forEachIndexed { i, page ->
            val lines = page.jsonArray
            if (i >= 2) assertEquals("صفحة ${i + 1}", 15, lines.size)
            for (line in lines) {
                if (line is JsonArray) {
                    for (seg in line) {
                        val id = seg.jsonArray[0].jsonPrimitive.int
                        val text = seg.jsonArray[1].jsonPrimitive.content
                        // الآيات بالترتيب: تستمر الآية نفسها أو تبدأ التالية
                        assertTrue("الآية $id في الصفحة ${i + 1}", id == nextAyah || id == nextAyah - 1)
                        if (id == nextAyah) nextAyah++
                        assertTrue(text.isNotBlank())
                    }
                } else {
                    val s = line.jsonPrimitive.content
                    when {
                        s == "B" -> bismillahs++
                        s.startsWith("H") -> headers++
                        else -> assertTrue("سطر فارغ غير متوقع في الصفحة ${i + 1}", i < 2)
                    }
                }
            }
        }
        assertEquals(6237, nextAyah)
        assertEquals(114, headers)
        assertEquals(112, bismillahs)
    }
}
