package app.yaqeen.data

import app.yaqeen.data.adhkar.AdhkarCatalog
import app.yaqeen.data.adhkar.AdhkarCatalogLoader
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.QuranDataLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class AdhkarCatalogTest {
    companion object {
        private lateinit var adhkar: AdhkarCatalog
        private lateinit var quran: QuranData

        private fun resource(path: String) =
            AdhkarCatalogTest::class.java.classLoader!!.getResourceAsStream(path)!!.readBytes().toString(Charsets.UTF_8)

        @JvmStatic
        @BeforeClass
        fun load() {
            adhkar = AdhkarCatalogLoader.parse(resource(AdhkarCatalogLoader.ASSET_PATH))
            quran = QuranDataLoader.parse(resource(QuranDataLoader.ASSET_PATH))
        }
    }

    @Test fun allRequiredCategoriesExistAndAreNotEmpty() {
        for (id in listOf("morning", "evening", "sleep", "waking", "after_prayer", "misc")) {
            val c = adhkar.category(id)
            assertNotNull("missing category $id", c)
            assertTrue("category $id is empty", c!!.items.isNotEmpty())
        }
    }

    @Test fun idsAreUniqueAndCountsPositive() {
        val ids = adhkar.categories.flatMap { it.items }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(adhkar.categories.flatMap { it.items }.all { it.count >= 1 && it.text.isNotBlank() && it.source.isNotBlank() })
    }

    @Test fun morningAndEveningAreDistinctSets() {
        val m = adhkar.category("morning")!!.items.map { it.text }
        val e = adhkar.category("evening")!!.items.map { it.text }
        // أصبحنا في الصباح وأمسينا في المساء: لا يجوز أن يظهر ذكر "أصبحنا وأصبح الملك" في المساء
        assertTrue(m.any { it.contains("أَصْبَحْنَا وَأَصْبَحَ") })
        assertTrue(e.none { it.contains("أَصْبَحْنَا وَأَصْبَحَ") })
        assertTrue(e.any { it.contains("أمسينا") || it.contains("أَمْسَيْنا") })
    }

    @Test fun quranVersesInAdhkarComeFromTheVerifiedMushafText() {
        val kursi = adhkar.category("morning")!!.items.first { it.quranRef == "2:255" }
        assertEquals(quran.ayah(2, 255).text, kursi.text)
        val lastBaqarah = adhkar.category("sleep")!!.items.first { it.quranRef == "2:285-286" }
        assertEquals(quran.ayah(2, 285).text + " " + quran.ayah(2, 286).text, lastBaqarah.text)
        val sleepQuls = adhkar.category("sleep")!!.items.first { it.quranRef == "112-114" }
        assertEquals(3, sleepQuls.count)
        assertTrue(sleepQuls.text.contains(quran.ayah(112, 1).text))
        assertTrue(sleepQuls.text.contains(quran.ayah(114, 6).text))
    }

    @Test fun tasbihAfterPrayerIs33And33And33() {
        val items = adhkar.category("after_prayer")!!.items
        assertEquals(listOf(33, 33, 33), items.filter { it.text in setOf("سُبْحَانَ اللَّهِ", "الْحَمْدُ لِلَّهِ", "اللَّهُ أَكْبَرُ") }.map { it.count })
    }

    @Test fun searchWorksAcrossCategories() {
        assertTrue(adhkar.search("الحي القيوم").isNotEmpty())
        assertTrue(adhkar.search("").isEmpty())
    }
}
