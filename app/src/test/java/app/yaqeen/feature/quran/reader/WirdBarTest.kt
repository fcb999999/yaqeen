package app.yaqeen.feature.quran.reader

import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.QuranDataLoader
import app.yaqeen.feature.quran.plans.PlanMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test

class WirdBarTest {
    companion object {
        private lateinit var quran: QuranData
        private lateinit var math: PlanMath

        @JvmStatic
        @BeforeClass
        fun load() {
            val stream = WirdBarTest::class.java.classLoader!!.getResourceAsStream(QuranDataLoader.ASSET_PATH)
                ?: error("quran.json غير موجود في موارد الاختبار")
            quran = QuranDataLoader.parse(String(stream.readBytes(), Charsets.UTF_8))
            math = PlanMath(quran)
        }
    }

    private val today = "2026-11-05"

    /** ورد صفحات من أول صفحة 1 إلى 20 (٥ صفحات يوميًا). */
    private fun plan(
        progress: Int = 0,
        status: String = "ACTIVE",
        start: String = "2026-11-01",
        daily: Int = 5,
        id: String = "p1",
        name: String = "ختمة",
    ): QuranPlanEntity {
        val first = quran.firstAyahOfPage(1).id
        val last = quran.ayahsOfPage(20).last().id
        return QuranPlanEntity(
            id = id, name = name, startDate = start, unit = "PAGE",
            startAyahId = first, endAyahId = last, dailyAmount = daily,
            progressAyahId = if (progress == 0) first - 1 else progress, status = status,
        )
    }

    private fun log(planId: String = "p1", ayahs: Int = 0, pages: Int = 0, met: Boolean = false, date: String = today) =
        QuranPlanLogEntity(id = "${planId}_$date", planId = planId, date = date, ayahsRead = ayahs, pagesRead = pages, targetMet = met)

    @Test fun noActivePlanMeansNoBar() {
        assertNull(WirdBarLogic.build(math, emptyList(), emptyList(), today))
        assertNull(WirdBarLogic.build(math, listOf(plan(status = "PAUSED")), emptyList(), today))
        assertNull(WirdBarLogic.build(math, listOf(plan(status = "COMPLETED")), emptyList(), today))
        // لم يبدأ بعد
        assertNull(WirdBarLogic.build(math, listOf(plan(start = "2026-12-01")), emptyList(), today))
    }

    @Test fun freshPlanShowsTheWholeDailyAmountRemaining() {
        val bar = WirdBarLogic.build(math, listOf(plan()), emptyList(), today)!!
        assertFalse(bar.met)
        assertEquals("باقي ٥ صفحات من ورد اليوم", bar.headline)
        assertEquals(0f, bar.ratio, 0.0001f)
        assertNotNull(bar.detail)
        assertTrue(bar.detail!!.startsWith("الورد كله: باقي"))
    }

    @Test fun progressInsideTodayReducesWhatRemains() {
        val firstOfPage3 = quran.firstAyahOfPage(3).id
        // قُرئت الصفحتان ١ و٢ اليوم (آخر آية في الصفحة ٢)
        val endOfPage2 = firstOfPage3 - 1
        val p = plan(progress = endOfPage2)
        val readToday = endOfPage2 - quran.firstAyahOfPage(1).id + 1
        val bar = WirdBarLogic.build(math, listOf(p), listOf(log(ayahs = readToday, pages = 2)), today)!!
        assertFalse(bar.met)
        assertEquals("باقي ٣ صفحات من ورد اليوم", bar.headline)
        assertTrue(bar.ratio > 0f && bar.ratio < 1f)
    }

    @Test fun reachingTheDailyPortionShowsDone() {
        val endOfPage5 = quran.ayahsOfPage(5).last().id
        val p = plan(progress = endOfPage5)
        val readToday = endOfPage5 - quran.firstAyahOfPage(1).id + 1
        val bar = WirdBarLogic.build(math, listOf(p), listOf(log(ayahs = readToday, pages = 5, met = true)), today)!!
        assertTrue(bar.met)
        assertEquals("أتممتَ ورد اليوم", bar.headline)
        assertEquals(1f, bar.ratio, 0f)
    }

    @Test fun unfinishedPlanIsPreferredOverFinishedOne() {
        val endOfPage5 = quran.ayahsOfPage(5).last().id
        val done = plan(progress = endOfPage5, id = "a", name = "منجز")
        val open = plan(id = "b", name = "مفتوح")
        val readToday = endOfPage5 - quran.firstAyahOfPage(1).id + 1
        val bar = WirdBarLogic.build(
            math, listOf(done, open),
            listOf(log("a", ayahs = readToday, pages = 5, met = true)), today,
        )!!
        assertEquals("b", bar.planId)
        assertFalse(bar.met)
    }

    @Test fun ignoresLogsOfOtherDays() {
        val yesterday = log(met = true, date = "2026-11-04")
        val bar = WirdBarLogic.build(math, listOf(plan()), listOf(yesterday), today)!!
        assertFalse(bar.met)
    }
}
