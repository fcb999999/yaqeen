package app.yaqeen.feature.quran

import app.yaqeen.data.local.BookmarkEntity
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.QuranDataLoader
import app.yaqeen.feature.quran.audio.AudioFiles
import app.yaqeen.feature.quran.audio.AudioPlaylists
import app.yaqeen.feature.quran.audio.AyahRepeatCounter
import app.yaqeen.feature.quran.audio.Reciters
import app.yaqeen.feature.quran.audio.SizeFormat
import app.yaqeen.feature.quran.bookmarks.BookmarkFilter
import app.yaqeen.feature.quran.bookmarks.BookmarkPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class AudioAndBookmarksTest {
    companion object {
        private lateinit var q: QuranData

        @JvmStatic
        @BeforeClass
        fun load() {
            val text = AudioAndBookmarksTest::class.java.classLoader!!.getResourceAsStream(QuranDataLoader.ASSET_PATH)!!
                .readBytes().toString(Charsets.UTF_8)
            q = QuranDataLoader.parse(text)
        }
    }

    // ---------------------------------------------------------- التكرار

    @Test fun repeatCounterPlaysExactlyNTimes() {
        for (n in 1..6) {
            val c = AyahRepeatCounter(n)
            var plays = 1
            var repeatOne = c.onNewItem()
            assertEquals(n > 1, repeatOne)
            // كل دورة إعادة تضيف مرة تشغيل ما دام الوضع "تكرار واحد" مفعّلًا
            while (repeatOne) {
                plays++
                repeatOne = c.onLooped()
            }
            assertEquals("n=$n", n, plays)
        }
    }

    @Test fun repeatCounterResetsOnNewItem() {
        val c = AyahRepeatCounter(3)
        c.onNewItem(); c.onLooped()
        assertEquals(2, c.played)
        assertTrue(c.onNewItem())
        assertEquals(1, c.played)
    }

    // ---------------------------------------------------------- قوائم التشغيل

    @Test fun surahPlaylistStartsAtRequestedAyah() {
        val (ids, start) = AudioPlaylists.surah(q, 2, 255)
        assertEquals(286, ids.size)
        assertEquals(8, ids.first())
        assertEquals(254, start)
        assertEquals(q.ayah(2, 255).id, ids[start])
        // قيم خارج النطاق تُقصّ
        assertEquals(0, AudioPlaylists.surah(q, 1, 0).second)
        assertEquals(6, AudioPlaylists.surah(q, 1, 99).second)
        assertEquals(6, AudioPlaylists.surah(q, 114, 1).first.size)
    }

    @Test fun rangePlaylist() {
        assertEquals(listOf(5, 6, 7), AudioPlaylists.range(6236, 5, 7))
        assertEquals(listOf(5, 6, 7), AudioPlaylists.range(6236, 7, 5))
        assertEquals(listOf(6236), AudioPlaylists.range(6236, 9000, 6236))
    }

    // ---------------------------------------------------------- القرّاء والملفات

    @Test fun recitersAreUniqueAndUrlsWellFormed() {
        assertEquals(Reciters.all.size, Reciters.all.map { it.id }.toSet().size)
        assertTrue(Reciters.all.all { it.bitrate in setOf(64, 128, 192) && it.id.startsWith("ar.") })
        assertEquals("https://cdn.islamic.network/quran/audio/128/ar.alafasy/262.mp3", Reciters.url(Reciters.byId("ar.alafasy"), 262))
        assertEquals(Reciters.DEFAULT_ID, Reciters.byId("غير موجود").id)
        assertEquals(Reciters.DEFAULT_ID, Reciters.byId(null).id)
    }

    @Test fun audioFilePaths() {
        val root = File("root")
        assertEquals(File(File(File(root, "audio"), "ar.alafasy"), "5.mp3"), AudioFiles.file(root, "ar.alafasy", 5))
        assertTrue(AudioFiles.partFile(root, "ar.alafasy", 5).name.endsWith(".mp3.part"))
    }

    @Test fun countBySurahIgnoresPartsAndJunk() {
        val names = listOf("1.mp3", "2.mp3", "7.mp3", "8.mp3", "9.mp3.part", "x.mp3", "99999.mp3", "6236.mp3", "notes.txt")
        val c = AudioFiles.countBySurah(names, q)
        assertEquals(3, c[1]) // 1,2,7
        assertEquals(1, c[2]) // 8
        assertEquals(1, c[114]) // 6236
        assertEquals(5, c.sum())
    }

    @Test fun sizeFormat() {
        assertEquals("٠ ك.ب", SizeFormat.bytes(0))
        assertEquals("١ ك.ب", SizeFormat.bytes(10))
        assertEquals("٥٠٠ ك.ب", SizeFormat.bytes(500 * 1024L))
        assertEquals("١٫٥ م.ب", SizeFormat.bytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("٢٫٠ ج.ب", SizeFormat.bytes(2L * 1024 * 1024 * 1024))
    }

    // ---------------------------------------------------------- العلامات

    private fun bm(surah: Int = 2, ayah: Int = 255, title: String = "", note: String = "", cat: String? = null) =
        BookmarkEntity(id = "b", surah = surah, ayah = ayah, page = 42, title = title, note = note, color = BookmarkPalette.default, categoryId = cat)

    @Test fun filterByCategory() {
        val a = bm(cat = null)
        val b = bm(cat = "c1")
        assertTrue(BookmarkFilter.matches(a, null, "", "البقرة", null))
        assertTrue(BookmarkFilter.matches(b, null, "", "البقرة", "ورد"))
        assertTrue(BookmarkFilter.matches(a, BookmarkFilter.UNCATEGORIZED, "", "البقرة", null))
        assertFalse(BookmarkFilter.matches(b, BookmarkFilter.UNCATEGORIZED, "", "البقرة", "ورد"))
        assertTrue(BookmarkFilter.matches(b, "c1", "", "البقرة", "ورد"))
        assertFalse(BookmarkFilter.matches(a, "c1", "", "البقرة", null))
    }

    @Test fun searchTitleNoteSurahAndNormalization() {
        val b = bm(title = "آية الكرسي", note = "أذكار النوم", cat = "c1")
        assertTrue(BookmarkFilter.matches(b, null, "الكرسي", "البقرة", "مهم"))
        assertTrue(BookmarkFilter.matches(b, null, "اية الكرسي", "البقرة", "مهم")) // تطبيع الهمزة
        assertTrue(BookmarkFilter.matches(b, null, "النوم", "البقرة", "مهم"))
        assertTrue(BookmarkFilter.matches(b, null, "البقره", "البقرة", "مهم")) // تاء مربوطة
        assertTrue(BookmarkFilter.matches(b, null, "مهم", "البقرة", "مهم"))
        assertTrue(BookmarkFilter.matches(b, null, "الكرسي النوم", "البقرة", "مهم")) // كل الكلمات
        assertFalse(BookmarkFilter.matches(b, null, "الكرسي صلاة", "البقرة", "مهم"))
    }

    @Test fun deterministicIdAndPalette() {
        assertEquals("bm_2_255", BookmarkFilter.deterministicId(2, 255))
        assertEquals(8, BookmarkPalette.colors.size)
        assertEquals(8, BookmarkPalette.colors.toSet().size)
        assertTrue(BookmarkPalette.isLight(0xFFFFFFFF.toInt()))
        assertFalse(BookmarkPalette.isLight(0xFF000000.toInt()))
    }
}
