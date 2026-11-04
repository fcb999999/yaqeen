package app.yaqeen.feature.quran.reader

import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.QuranDataLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class ReaderLogicTest {
    companion object {
        private lateinit var quran: QuranData

        @JvmStatic
        @BeforeClass
        fun load() {
            val stream = ReaderLogicTest::class.java.classLoader!!.getResourceAsStream(QuranDataLoader.ASSET_PATH)
                ?: error("quran.json غير موجود في موارد الاختبار")
            quran = QuranDataLoader.parse(String(stream.readBytes(), Charsets.UTF_8))
        }
    }

    // ---------------- علامة نهاية الآية والمراجع

    @Test fun endMarkerUsesArabicIndicDigits() {
        assertEquals("۝١", QuranText.endMarker(1))
        assertEquals("۝٢٥٥", QuranText.endMarker(255))
        assertEquals("۝٢٨٦", QuranText.endMarker(286))
    }

    @Test fun plainSurahNameStripsDiacritics() {
        assertEquals("البقرة", QuranText.plainSurahName(quran.surah(2).nameAr))
        assertEquals("الفاتحة", QuranText.plainSurahName(quran.surah(1).nameAr))
        assertEquals("آل عمران", QuranText.plainSurahName(quran.surah(3).nameAr))
        assertEquals("المائدة", QuranText.plainSurahName(quran.surah(5).nameAr))
    }

    @Test fun shareTextFormat() {
        val a = quran.ayah(2, 255)
        val t = QuranText.shareText(a.text, quran.surah(2).nameAr, a.number)
        assertEquals("«${a.text}» [البقرة: ٢٥٥]", t)
        // النص لم يُمسّ
        assertTrue(t.contains(a.text))
    }

    // ---------------- ترويسة السورة

    @Test fun pageOneHasFatihaHeaderWithoutSeparateBismillah() {
        val b = PageLayout.blocks(quran, 1)
        val header = b.filterIsInstance<PageBlock.SurahHeader>().single()
        assertEquals(1, header.surah.number)
        assertFalse(header.showBismillah)
    }

    @Test fun pageTwoStartsBaqaraWithBismillah() {
        val b = PageLayout.blocks(quran, 2)
        val header = b.filterIsInstance<PageBlock.SurahHeader>().single()
        assertEquals(2, header.surah.number)
        assertTrue(header.showBismillah)
        // الترويسة قبل أول آية
        val firstRun = b.filterIsInstance<PageBlock.AyahRun>().first()
        assertEquals(2, firstRun.ayahs.first().surah)
        assertEquals(1, firstRun.ayahs.first().number)
    }

    @Test fun tawbaHasNoBismillah() {
        val page = quran.ayah(9, 1).page
        val header = PageLayout.blocks(quran, page).filterIsInstance<PageBlock.SurahHeader>().single { it.surah.number == 9 }
        assertFalse(header.showBismillah)
    }

    @Test fun lastPageHasThreeSurahHeadersInOrder() {
        val headers = PageLayout.blocks(quran, 604).filterIsInstance<PageBlock.SurahHeader>().map { it.surah.number }
        assertEquals(listOf(112, 113, 114), headers)
    }

    @Test fun everyPageCoversItsAyahsExactlyOnceAndHeadersMatchFirstAyahs() {
        var total = 0
        for (p in 1..quran.pageCount) {
            val blocks = PageLayout.blocks(quran, p)
            val runAyahs = blocks.filterIsInstance<PageBlock.AyahRun>().flatMap { it.ayahs }
            assertEquals("page $p", quran.ayahsOfPage(p), runAyahs)
            total += runAyahs.size
            val headerSurahs = blocks.filterIsInstance<PageBlock.SurahHeader>().map { it.surah.number }
            assertEquals("headers on page $p", quran.surahsStartingOnPage(p).map { it.number }, headerSurahs)
        }
        assertEquals(6236, total)
    }

    @Test fun pageInfoComesFromFirstAyah() {
        val i = PageLayout.info(quran, 22)
        assertEquals(2, i.juz)
        assertEquals(2, i.surah.number)
        assertEquals(PageLayout.info(quran, 604).surah.number, 112)
        assertEquals(1, PageLayout.info(quran, -5).page)
        assertEquals(604, PageLayout.info(quran, 9999).page)
    }

    // ---------------- أرباع الأحزاب

    @Test fun hizbLabels() {
        assertEquals("الحزب ١", HizbLabels.label(1))
        assertEquals("ربع الحزب ١", HizbLabels.label(2))
        assertEquals("نصف الحزب ١", HizbLabels.label(3))
        assertEquals("ثلاثة أرباع الحزب ١", HizbLabels.label(4))
        assertEquals("الحزب ٢", HizbLabels.label(5))
        assertEquals("ثلاثة أرباع الحزب ٦٠", HizbLabels.label(240))
        assertEquals(60, HizbLabels.hizbOf(240))
    }

    @Test fun markLabelNamesJuzStarts() {
        assertEquals("بداية الجزء ١ · الحزب ١", HizbLabels.markLabel(1))
        assertEquals("الحزب ٢", HizbLabels.markLabel(5)) // الحزب الثاني لا يبدأ جزءًا
        assertEquals("بداية الجزء ٢ · الحزب ٣", HizbLabels.markLabel(9))
        assertEquals("ربع الحزب ١", HizbLabels.markLabel(2))
    }

    @Test fun quarterStartsProduceHizbMarksOncePerQuarter() {
        var marks = 0
        for (p in 1..quran.pageCount) marks += PageLayout.blocks(quran, p).count { it is PageBlock.HizbMark }
        assertEquals(240, marks)
        assertEquals(240, IndexFilter.quarters(quran).size)
        assertEquals(30, IndexFilter.juzStarts(quran).size)
    }

    // ---------------- تنسيق الكتلة

    @Test fun runFormatSpansAreContiguousAndMarkerIsAtTheEnd() {
        val ayahs = quran.ayahsOfPage(2)
        val (text, spans) = RunFormat.build(ayahs)
        assertEquals(ayahs.size, spans.size)
        spans.forEachIndexed { i, s ->
            assertEquals(ayahs[i].id, s.ayahId)
            assertEquals(ayahs[i].text, text.substring(s.start, s.markerStart - 1))
            assertEquals(QuranText.endMarker(ayahs[i].number), text.substring(s.markerStart, s.end))
            if (i > 0) assertEquals(spans[i - 1].end + 1, s.start)
        }
        assertEquals(spans.last().end, text.length)
        assertEquals(spans[1].ayahId, RunFormat.ayahAt(spans, spans[1].start + 3))
        assertNull(RunFormat.ayahAt(spans, spans[0].end)) // الفاصل
    }

    // ---------------- الاستئناف

    private fun pt(dev: String, t: Long, page: Int = 10, name: String = "") =
        ProgressPoint(dev, name, 2, 5, page, 1, t)

    @Test fun resumeEmptyIsNull() = assertNull(Resume.select(emptyList(), "me"))

    @Test fun resumePicksNewestAcrossDevices() {
        val r = Resume.select(listOf(pt("me", 100), pt("tablet", 300, name = "Pad"), pt("old", 200)), "me")!!
        assertEquals("tablet", r.point.deviceId)
        assertTrue(r.fromOtherDevice)
    }

    @Test fun resumeOwnDeviceNewestIsNotOtherDevice() {
        val r = Resume.select(listOf(pt("me", 500), pt("tablet", 300)), "me")!!
        assertEquals("me", r.point.deviceId)
        assertFalse(r.fromOtherDevice)
    }

    @Test fun resumeTieIsDeterministicAndPrefersOwnDevice() {
        val a = Resume.select(listOf(pt("b", 100), pt("me", 100), pt("z", 100)), "me")!!
        assertEquals("me", a.point.deviceId)
        val b = Resume.select(listOf(pt("b", 100), pt("z", 100)), "me")!!
        assertEquals("z", b.point.deviceId)
        val c = Resume.select(listOf(pt("z", 100), pt("b", 100)), "me")!!
        assertEquals("z", c.point.deviceId)
    }

    @Test fun resumeIgnoresInvalidRecords() {
        val bad = listOf(pt("x", 900, page = 0), pt("y", 800, page = 700), pt("w", 700).copy(surah = 0), pt("ok", 50))
        val r = Resume.select(bad, "me")!!
        assertEquals("ok", r.point.deviceId)
        assertNull(Resume.select(bad.dropLast(1), "me"))
    }

    @Test fun resumeSnackbarText() {
        assertEquals("استئناف من آخر موضع على جهاز Pixel 8", Resume.snackbarText("Pixel 8"))
        assertEquals("استئناف من آخر موضع على جهاز آخر", Resume.snackbarText("  "))
    }

    // ---------------- التمرير التلقائي

    @Test fun autoScrollSpeedIsClampedAndMonotonic() {
        assertEquals(1, AutoScroll.clampSpeed(-3))
        assertEquals(10, AutoScroll.clampSpeed(99))
        for (s in 1..9) assertTrue(AutoScroll.dpPerSecond(s) < AutoScroll.dpPerSecond(s + 1))
        assertTrue(AutoScroll.presets.all { it.speed in 1..10 })
        assertEquals("بطيء", AutoScroll.levelName(1))
        assertEquals("أسرع", AutoScroll.levelName(10))
    }

    @Test fun autoScrollNeverWrapsAtMushafEnd() {
        assertEquals(2, AutoScroll.nextPage(1, 604))
        assertEquals(604, AutoScroll.nextPage(603, 604))
        assertNull(AutoScroll.nextPage(604, 604))
        assertNull(AutoScroll.nextPage(605, 604))
    }

    @Test fun autoScrollMovesThenHoldsThenAdvances() {
        // تمرير: لم نبلغ نهاية الصفحة
        var t = AutoScroll.tick(offset = 0f, maxOffset = 100f, holdLeft = 50f, dtSeconds = 0.1f, pxPerSecond = 200f, isLastPage = false)
        assertEquals(20f, t.scrollBy, 0.001f)
        assertEquals(AutoScroll.Outcome.Continue, t.outcome)
        // لا يتجاوز نهاية الصفحة
        t = AutoScroll.tick(95f, 100f, 50f, 0.1f, 400f, false)
        assertEquals(5f, t.scrollBy, 0.001f)
        // إمساك عند النهاية
        t = AutoScroll.tick(100f, 100f, 50f, 0.1f, 200f, false)
        assertEquals(0f, t.scrollBy, 0f)
        assertEquals(30f, t.holdLeft, 0.001f)
        assertEquals(AutoScroll.Outcome.Continue, t.outcome)
        // انتهى الإمساك: الصفحة التالية
        t = AutoScroll.tick(100f, 100f, 10f, 0.1f, 200f, false)
        assertEquals(AutoScroll.Outcome.NextPage, t.outcome)
    }

    @Test fun autoScrollStopsAtLastPage() {
        val t = AutoScroll.tick(100f, 100f, 5f, 0.1f, 200f, isLastPage = true)
        assertEquals(AutoScroll.Outcome.EndOfMushaf, t.outcome)
        // صفحة قصيرة لا تحتاج تمريرًا (maxOffset = 0) تنتقل بعد الإمساك
        var hold = 30f
        var steps = 0
        var outcome: AutoScroll.Outcome = AutoScroll.Outcome.Continue
        while (outcome == AutoScroll.Outcome.Continue && steps < 1000) {
            val tk = AutoScroll.tick(0f, 0f, hold, 0.016f, 100f, isLastPage = false)
            hold = tk.holdLeft; outcome = tk.outcome; steps++
        }
        assertEquals(AutoScroll.Outcome.NextPage, outcome)
        assertTrue(steps in 10..30)
    }

    @Test fun autoScrollHitchFramesAreCapped() {
        val t = AutoScroll.tick(0f, 1000f, 0f, dtSeconds = 5f, pxPerSecond = 100f, isLastPage = false)
        assertEquals(10f, t.scrollBy, 0.001f)
    }

    // ---------------- القفز

    @Test fun jumpParsing() {
        assertEquals(255, JumpParser.ayahNumber("٢٥٥", 286))
        assertEquals(255, JumpParser.ayahNumber("255", 286))
        assertNull(JumpParser.ayahNumber("287", 286))
        assertNull(JumpParser.ayahNumber("0", 286))
        assertNull(JumpParser.ayahNumber("", 286))
        assertNull(JumpParser.ayahNumber("abc", 286))
        assertEquals(604, JumpParser.page("٦٠٤"))
        assertNull(JumpParser.page("605"))
        assertNull(JumpParser.page("99999999999"))
        assertEquals(30, JumpParser.juz("30"))
        assertNull(JumpParser.juz("31"))
        assertEquals("123", JumpParser.digits("١2٣x"))
    }

    // ---------------- الفهرس

    @Test fun surahFilter() {
        assertEquals(114, IndexFilter.surahs(quran, "").size)
        assertEquals(listOf(2), IndexFilter.surahs(quran, "البقرة").map { it.number })
        assertEquals(listOf(2), IndexFilter.surahs(quran, "سورة البقره").map { it.number })
        assertTrue(IndexFilter.surahs(quran, "عمران").any { it.number == 3 })
        assertEquals(listOf(112), IndexFilter.surahs(quran, "١١٢").map { it.number })
        assertTrue(IndexFilter.surahs(quran, "1").map { it.number }.containsAll(listOf(1, 10, 11, 100, 114)))
        assertTrue(IndexFilter.surahs(quran, "zzzz").isEmpty())
        assertTrue(IndexFilter.surahs(quran, "baqara").any { it.number == 2 })
    }

    @Test fun juzAndQuarterFilters() {
        val starts = IndexFilter.juzStarts(quran)
        assertEquals(1, IndexFilter.juz(quran, starts, "30").size)
        assertEquals(30, IndexFilter.juz(quran, starts, "").size)
        assertEquals(listOf(30), IndexFilter.juz(quran, starts, "النبأ").map { it.juz })
        val q = IndexFilter.quarters(quran)
        assertEquals(4, IndexFilter.quarters(quran, q, "٥").size)
        assertEquals(60, IndexFilter.quarters(quran, q, "نصف").size)
        assertEquals(240, IndexFilter.quarters(quran, q, "").size)
    }

    // ---------------- إبراز البحث

    @Test fun searchHighlightMapsBackToDiacriticText() {
        val a = quran.ayah(112, 1)
        val rs = SearchHighlight.ranges(a.text, "الله احد")
        assertEquals(2, rs.size)
        val hit = a.text.substring(rs[0].first, rs[0].last + 1)
        assertEquals("الله", app.yaqeen.core.ArabicText.normalize(hit))
    }

    @Test fun searchHighlightHandlesMultiWordAndOverlap() {
        val text = "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ"
        val rs = SearchHighlight.ranges(text, "اله الا")
        assertTrue(rs.isNotEmpty())
        for (r in rs) assertTrue(r.first >= 0 && r.last < text.length)
        assertTrue(SearchHighlight.ranges(text, "").isEmpty())
        assertTrue(SearchHighlight.ranges(text, "كلمةغائبة").isEmpty())
    }

    @Test fun everySearchHitHasAtLeastOneHighlight() {
        val hits = quran.search("الحي القيوم")
        assertTrue(hits.isNotEmpty())
        for (h in hits) assertTrue(SearchHighlight.ranges(h.text, "الحي القيوم").isNotEmpty())
        assertNotNull(hits.firstOrNull { it.surah == 2 && it.number == 255 })
    }

    // ---------------- الأوراد

    @Test fun planMath() {
        assertEquals(0f, PlanMath.fraction(1, 100, 0), 0f)
        assertEquals(0f, PlanMath.fraction(10, 109, 9), 0f)
        assertEquals(0.5f, PlanMath.fraction(1, 100, 50), 0.0001f)
        assertEquals(1f, PlanMath.fraction(1, 100, 100), 0f)
        assertEquals(1f, PlanMath.fraction(1, 100, 500), 0f)
        assertEquals(0f, PlanMath.fraction(1, 0, 0), 0f)
        assertEquals(100, PlanMath.total(1, 100))
        assertEquals(51, PlanMath.done(10, 109, 60))
    }
}
