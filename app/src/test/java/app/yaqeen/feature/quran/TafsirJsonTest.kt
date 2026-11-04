package app.yaqeen.feature.quran

import app.yaqeen.feature.quran.tafsir.TafsirCatalog
import app.yaqeen.feature.quran.tafsir.TafsirJson
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.io.StringReader

class TafsirJsonTest {
    private val json = Json

    private fun apiJson(texts: List<List<String>>): String {
        val surahs = texts.mapIndexed { i, ayahs ->
            val items = ayahs.mapIndexed { j, t -> "{\"number\":${j + 1},\"text\":${TafsirJson.quote(t)},\"numberInSurah\":${j + 1},\"juz\":1}" }
            "{\"number\":${i + 1},\"name\":\"س\",\"ayahs\":[${items.joinToString(",")}]}"
        }
        return "{\"code\":200,\"status\":\"OK\",\"data\":{\"surahs\":[${surahs.joinToString(",")}],\"edition\":{\"identifier\":\"x\",\"name\":\"n\"}}}"
    }

    @Test fun extractsTextsInOrderIgnoringOtherStrings() {
        val src = apiJson(listOf(listOf("أ", "ب"), listOf("ج")))
        val out = ArrayList<String>()
        TafsirJson.extractAyahTexts(StringReader(src)) { out += it }
        assertEquals(listOf("أ", "ب", "ج"), out)
    }

    @Test fun handlesEscapesAndUnicode() {
        val tricky = "سطر\nثانٍ \"اقتباس\" \\ شرطة \t تاب /"
        val src = apiJson(listOf(listOf(tricky, "A\u0001B")))
        val out = ArrayList<String>()
        TafsirJson.extractAyahTexts(StringReader(src)) { out += it }
        assertEquals(listOf(tricky, "A\u0001B"), out)
        // u-escapes
        val raw = "{\"data\":{\"surahs\":[{\"ayahs\":[{\"text\":\"\\u0627\\u0628\"}]}]}}"
        val o2 = ArrayList<String>()
        TafsirJson.extractAyahTexts(StringReader(raw)) { o2 += it }
        assertEquals(listOf("اب"), o2)
    }

    @Test fun invalidJsonThrows() {
        var thrown = false
        try { TafsirJson.extractAyahTexts(StringReader("{\"data\":{\"surahs\":[")) { } } catch (e: IllegalArgumentException) { thrown = true }
        assertTrue(thrown)
    }

    @Test fun quoteRoundTripsThroughRealJsonParser() {
        for (s in listOf("", "بسيط", "فيه \"اقتباس\" و\\ و\n و\r و\t", "\u0000\u001f", "𝒜 emoji 😀")) {
            assertEquals(s, json.parseToJsonElement(TafsirJson.quote(s)).jsonPrimitive.content)
        }
    }

    @Test fun writerProducesBundledFormatWithWorkingIndex() {
        val texts = (1..TafsirJson.AYAH_COUNT).map { i -> if (i % 7 == 0) "" else "نص التفسير رقم $i مع \"علامات\" و\nسطر" }
        val bos = ByteArrayOutputStream()
        val w = TafsirJson.FileWriter(bos, "ar.test", "كتاب", "مؤلف")
        texts.forEach { w.add(it) }
        w.finish()
        assertEquals(TafsirJson.AYAH_COUNT, w.count)
        val bytes = bos.toByteArray()

        // الصيغة: نفس مفاتيح الملف المضمَّن
        val root = json.parseToJsonElement(String(bytes, Charsets.UTF_8)).jsonObject
        assertEquals(setOf("edition", "name", "author", "texts"), root.keys)
        assertEquals("ar.test", root.getValue("edition").jsonPrimitive.content)
        assertEquals(texts, root.getValue("texts").jsonArray.map { it.jsonPrimitive.content })

        // الفهرس يشير إلى الموضع الصحيح لكل آية
        val dir = createTempDir("tafsir-test")
        try {
            val jf = File(dir, "x.json").also { it.writeBytes(bytes) }
            val xf = File(dir, "x.idx")
            w.writeIndex(xf)
            assertEquals(TafsirJson.AYAH_COUNT, TafsirJson.indexCount(xf))
            RandomAccessFile(xf, "r").use { idx ->
                RandomAccessFile(jf, "r").use { data ->
                    for (id in listOf(1, 2, 7, 3000, 6235, 6236)) {
                        val (start, len) = TafsirJson.readIndexEntry(idx, id)!!
                        val b = ByteArray(len)
                        data.seek(start.toLong()); data.readFully(b)
                        assertEquals(texts[id - 1], json.parseToJsonElement(String(b, Charsets.UTF_8)).jsonPrimitive.content)
                    }
                    assertNull(TafsirJson.readIndexEntry(idx, 0))
                    assertNull(TafsirJson.readIndexEntry(idx, 6237))
                }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun invalidIndexIsRejected() {
        val dir = createTempDir("tafsir-test")
        try {
            val f = File(dir, "bad.idx").also { it.writeBytes(byteArrayOf(1, 2, 3)) }
            RandomAccessFile(f, "r").use { assertNull(TafsirJson.readIndexEntry(it, 1)) }
            assertEquals(-1, TafsirJson.indexCount(f))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun streamingConversionEndToEnd() {
        val ayahs = listOf((1..7).map { "فاتحة $it" }, (1..286).map { "بقرة $it" }) + List(112) { listOf("س${it + 3}") }
        val src = apiJson(ayahs)
        val bos = ByteArrayOutputStream()
        val w = TafsirJson.FileWriter(bos, "e", "n", "a")
        TafsirJson.extractAyahTexts(StringReader(src)) { w.add(it) }
        w.finish()
        val back = json.parseToJsonElement(String(bos.toByteArray(), Charsets.UTF_8)).jsonObject.getValue("texts").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(ayahs.flatten(), back)
    }

    @Test fun paragraphsSplitWithoutAlteringText() {
        val long = (1..800).joinToString(" ") { "كلمة$it" }
        val text = "فقرة أولى\n\n  فقرة ثانية  \n$long"
        val p = TafsirJson.paragraphs(text, 500)
        assertEquals("فقرة أولى", p[0])
        assertEquals("فقرة ثانية", p[1])
        assertTrue(p.size > 4)
        assertTrue(p.drop(2).all { it.length <= 500 + 300 })
        // إعادة الوصل تعطي الكلمات نفسها بالترتيب
        assertEquals(long, p.drop(2).joinToString(" "))
        assertTrue(TafsirJson.paragraphs("   \n  ").isEmpty())
    }

    @Test fun bundledMuyassarAssetMatchesExpectedShape() {
        val raw = TafsirJsonTest::class.java.classLoader!!.getResourceAsStream("tafsir/ar.muyassar.json")!!.readBytes().toString(Charsets.UTF_8)
        val root = json.parseToJsonElement(raw).jsonObject
        assertEquals(setOf("edition", "name", "author", "texts"), root.keys)
        assertEquals(TafsirJson.AYAH_COUNT, root.getValue("texts").jsonArray.size)
        assertEquals("ar.muyassar", root.getValue("edition").jsonPrimitive.content)
        assertTrue(root.getValue("texts").jsonArray.all { it.jsonPrimitive.content.isNotBlank() })
    }

    /**
     * اختبار اختياري على ملفات حقيقية نُزّلت من الخدمة: يُشغَّل فقط إن حُدد المتغير YAQEEN_TAFSIR_SAMPLES
     * بمسارات ملفات JSON مفصولة بفاصلة منقوطة (يتخطاه الاختبار الافتراضي).
     */
    @Test fun realDownloadedFilesConvertAndIndexCorrectly() {
        val paths = System.getenv("YAQEEN_TAFSIR_SAMPLES")?.split(';')?.filter { it.isNotBlank() }.orEmpty()
        org.junit.Assume.assumeTrue(paths.isNotEmpty())
        for (p in paths) {
            val src = File(p)
            val expected = json.parseToJsonElement(src.readText(Charsets.UTF_8)).jsonObject
                .getValue("data").jsonObject.getValue("surahs").jsonArray
                .flatMap { it.jsonObject.getValue("ayahs").jsonArray }.map { it.jsonObject.getValue("text").jsonPrimitive.content }
            assertEquals(TafsirJson.AYAH_COUNT, expected.size)
            val dir = createTempDir("tafsir-real")
            try {
                val jf = File(dir, "e.json")
                val xf = File(dir, "e.idx")
                val w = TafsirJson.FileWriter(jf.outputStream(), "e", "n", "a")
                src.reader(Charsets.UTF_8).buffered(64 * 1024).use { TafsirJson.extractAyahTexts(it) { t -> w.add(t) } }
                w.finish(); w.writeIndex(xf)
                assertEquals(TafsirJson.AYAH_COUNT, w.count)
                RandomAccessFile(xf, "r").use { idx ->
                    RandomAccessFile(jf, "r").use { data ->
                        for (id in 1..TafsirJson.AYAH_COUNT step 37) {
                            val (start, len) = TafsirJson.readIndexEntry(idx, id)!!
                            val b = ByteArray(len)
                            data.seek(start.toLong()); data.readFully(b)
                            assertEquals("${src.name} #$id", expected[id - 1], json.parseToJsonElement(String(b, Charsets.UTF_8)).jsonPrimitive.content)
                        }
                    }
                }
            } finally {
                dir.deleteRecursively()
            }
        }
    }

    @Test fun catalogIsConsistent() {
        assertEquals(TafsirCatalog.all.size, TafsirCatalog.all.map { it.id }.toSet().size)
        assertNotNull(TafsirCatalog.byId(TafsirCatalog.DEFAULT))
        assertTrue(TafsirCatalog.byId(TafsirCatalog.DEFAULT)!!.bundled)
        assertEquals(1, TafsirCatalog.all.count { it.bundled })
        assertTrue(TafsirCatalog.all.none { it.id == "ar.miqbas" })
    }
}
