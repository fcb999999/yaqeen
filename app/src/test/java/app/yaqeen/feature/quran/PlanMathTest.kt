package app.yaqeen.feature.quran

import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.quran.QuranData
import app.yaqeen.data.quran.QuranDataLoader
import app.yaqeen.domain.model.QuranPlanUnit
import app.yaqeen.feature.quran.plans.DayStatus
import app.yaqeen.feature.quran.plans.PlanFormat
import app.yaqeen.feature.quran.plans.PlanMath
import app.yaqeen.feature.quran.plans.Portion
import app.yaqeen.feature.quran.reminders.WirdReminders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.time.LocalDate

class PlanMathTest {
    companion object {
        private lateinit var q: QuranData
        private lateinit var m: PlanMath

        @JvmStatic
        @BeforeClass
        fun load() {
            val text = PlanMathTest::class.java.classLoader!!.getResourceAsStream(QuranDataLoader.ASSET_PATH)!!
                .readBytes().toString(Charsets.UTF_8)
            q = QuranDataLoader.parse(text)
            m = PlanMath(q)
        }
    }

    private fun plan(
        unit: QuranPlanUnit = QuranPlanUnit.PAGE, start: Int = 1, end: Int = 6236, daily: Int = 1,
        progress: Int = start - 1, startDate: String = "2026-11-01", endDate: String? = null,
    ) = QuranPlanEntity(
        id = "p", name = "ورد", startDate = startDate, endDate = endDate, unit = unit.name,
        startAyahId = start, endAyahId = end, dailyAmount = daily, progressAyahId = progress,
    )

    // ---------------------------------------------------------- تحويل الوحدات

    @Test fun pageAndJuzBoundaries() {
        assertEquals(1, m.firstAyahOfPage(1))
        assertEquals(7, m.lastAyahOfPage(1))
        assertEquals(8, m.firstAyahOfPage(2)) // البقرة ١
        assertEquals(1, m.firstAyahOfJuz(1))
        assertEquals(q.firstAyahOfJuz(2).id, m.firstAyahOfJuz(2))
        assertEquals(m.firstAyahOfJuz(2) - 1, m.lastAyahOfJuz(1))
        assertEquals(6236, m.lastAyahOfJuz(30))
        assertEquals(6236, m.lastAyahOfPage(604))
        for (p in 1..604) {
            assertEquals(p, q.ayah(m.firstAyahOfPage(p)).page)
            assertEquals(p, q.ayah(m.lastAyahOfPage(p)).page)
        }
        for (j in 1..30) {
            assertEquals(j, q.ayah(m.firstAyahOfJuz(j)).juz)
            assertEquals(j, q.ayah(m.lastAyahOfJuz(j)).juz)
        }
    }

    @Test fun unitCounts() {
        assertEquals(604, m.unitCount(QuranPlanUnit.PAGE, 1, 6236))
        assertEquals(30, m.unitCount(QuranPlanUnit.JUZ, 1, 6236))
        assertEquals(6236, m.unitCount(QuranPlanUnit.AYAH, 1, 6236))
        assertEquals(0, m.unitCount(QuranPlanUnit.PAGE, 10, 5))
        // سورة الفاتحة كلها صفحة واحدة
        assertEquals(1, m.unitCount(QuranPlanUnit.PAGE, 1, 7))
        assertEquals(6, m.totalUnits(plan(QuranPlanUnit.AYAH, 1, 6)))
    }

    // ---------------------------------------------------------- المقطع اليومي

    @Test fun portionByPages() {
        val p = plan(QuranPlanUnit.PAGE, 1, 6236, daily = 2)
        val portion = m.todayPortion(p, 0)!!
        assertEquals(1, portion.fromAyah)
        assertEquals(m.lastAyahOfPage(2), portion.toAyah)
        // اليوم التالي يبدأ بعد نهاية صفحة ٢
        val next = m.todayPortion(p.copy(progressAyahId = portion.toAyah), portion.toAyah)!!
        assertEquals(m.firstAyahOfPage(3), next.fromAyah)
        assertEquals(m.lastAyahOfPage(4), next.toAyah)
    }

    @Test fun portionByJuzAndAyah() {
        val pj = plan(QuranPlanUnit.JUZ, 1, 6236, daily = 1)
        assertEquals(Portion(1, m.lastAyahOfJuz(1)), m.todayPortion(pj, 0))
        val pa = plan(QuranPlanUnit.AYAH, 8, 300, daily = 10)
        assertEquals(Portion(8, 17), m.todayPortion(pa, 7))
    }

    @Test fun portionClippedToPlanEnd() {
        val p = plan(QuranPlanUnit.PAGE, 8, 30, daily = 5)
        assertEquals(Portion(8, 30), m.todayPortion(p, 7))
        val pa = plan(QuranPlanUnit.AYAH, 8, 12, daily = 10, progress = 10)
        assertEquals(Portion(11, 12), m.todayPortion(pa, 10))
    }

    @Test fun startMidPageGivesRestOfPage() {
        // نبدأ في آية من منتصف صفحة ٣ بمقدار صفحة واحدة: بقية الصفحة ٣ فقط
        val mid = m.firstAyahOfPage(3) + 2
        val p = plan(QuranPlanUnit.PAGE, mid, 6236, daily = 1)
        assertEquals(Portion(mid, m.lastAyahOfPage(3)), m.todayPortion(p, mid - 1))
    }

    @Test fun portionNullWhenFinished() {
        val p = plan(QuranPlanUnit.PAGE, 1, 7, daily = 1, progress = 7)
        assertNull(m.todayPortion(p, 7))
    }

    @Test fun startOfDayIsStableAfterReading() {
        val p = plan(QuranPlanUnit.PAGE, 1, 6236, daily = 2)
        val first = m.todayPortion(p, 0)!!
        // أنهى المستخدم المقطع: progress = نهاية المقطع وآيات اليوم = حجمه
        val done = p.copy(progressAyahId = first.toAyah)
        assertEquals(0, m.startOfDayProgress(done, first.toAyah))
        assertEquals(first, m.todayPortion(done, QuranPlanLogEntity("p_d", "p", "d", ayahsRead = first.toAyah)))
    }

    @Test fun targetMetRules() {
        val p = plan(QuranPlanUnit.PAGE, 1, 6236, daily = 2)
        val end = m.todayPortion(p, 0)!!.toAyah
        assertFalse(m.isTargetMet(p.copy(progressAyahId = 7), 7, 7))
        assertFalse(m.isTargetMet(p.copy(progressAyahId = end - 1), end - 1, end - 1))
        assertTrue(m.isTargetMet(p.copy(progressAyahId = end), end, end))
        assertTrue(m.isTargetMet(p.copy(progressAyahId = end + 30), end + 30, end + 30)) // قرأ أكثر
        // اكتمال الورد يحقق المقدار حتى لو كان أقل من المقطع
        val small = plan(QuranPlanUnit.AYAH, 1, 3, daily = 10)
        assertTrue(m.isTargetMet(small.copy(progressAyahId = 3), 3, 3))
    }

    // ---------------------------------------------------------- التقدم بقراءة صفحة

    @Test fun advanceContiguousPage() {
        val p = plan(QuranPlanUnit.PAGE, 1, 6236, progress = 0)
        assertEquals(m.lastAyahOfPage(1), m.advanceForPage(p, 1))
        assertEquals(m.lastAyahOfPage(2), m.advanceForPage(p.copy(progressAyahId = 7), 2))
    }

    @Test fun noAdvanceWhenGapOrBehind() {
        val p = plan(QuranPlanUnit.PAGE, 1, 6236, progress = 7)
        assertNull(m.advanceForPage(p, 5)) // فجوة
        assertNull(m.advanceForPage(p, 1)) // سبق إنجازها
        assertNull(m.advanceForPage(p, 0))
        assertNull(m.advanceForPage(p, 605))
    }

    @Test fun advanceClippedToPlanRange() {
        // يبدأ الورد في منتصف صفحة ٣
        val mid = m.firstAyahOfPage(3) + 2
        val p = plan(QuranPlanUnit.PAGE, mid, mid + 4, progress = mid - 1)
        assertEquals(mid + 4, m.advanceForPage(p, 3)) // لا يتجاوز نهاية الورد
        assertNull(m.advanceForPage(p, 2)) // قبل المدى
        assertNull(m.advanceForPage(p, 10)) // بعد المدى
    }

    @Test fun midPageProgressThenSamePageAgain() {
        val last = m.lastAyahOfPage(3)
        val p = plan(QuranPlanUnit.PAGE, 1, 6236, progress = last - 2)
        assertEquals(last, m.advanceForPage(p, 3))
        assertNull(m.advanceForPage(p.copy(progressAyahId = last), 3))
    }

    // ---------------------------------------------------------- النسبة والاقتراح

    @Test fun fraction() {
        assertEquals(0f, m.fraction(1, 6236, 0), 0.0001f)
        assertEquals(1f, m.fraction(1, 6236, 6236), 0.0001f)
        val half = m.fraction(1, 6236, 3118)
        assertTrue(half in 0.3f..0.7f)
        assertTrue(m.fraction(1, 100, 50) > m.fraction(1, 100, 10))
    }

    @Test fun suggestedDaily() {
        assertEquals(21, PlanMath.suggestedDaily(604, 30)) // 20.13 -> 21
        assertEquals(1, PlanMath.suggestedDaily(30, 30))
        assertEquals(2, PlanMath.suggestedDaily(31, 30))
        assertEquals(1, PlanMath.suggestedDaily(0, 10))
        assertEquals(1, PlanMath.suggestedDaily(10, 0))
        assertEquals(20, PlanMath.suggestedDaily(604, 31)) // 19.48 -> 20
    }

    @Test fun remainingUnits() {
        val p = plan(QuranPlanUnit.PAGE, 1, 6236, progress = m.lastAyahOfPage(10))
        assertEquals(594, m.remainingUnits(p))
        assertEquals(0, m.remainingUnits(p.copy(progressAyahId = 6236)))
        assertEquals(6236 - m.lastAyahOfPage(10), m.remainingAyahs(p))
    }

    // ---------------------------------------------------------- التواريخ

    @Test fun dates() {
        val a = LocalDate.parse("2026-11-01")
        assertEquals(30, PlanMath.daysInclusive(a, LocalDate.parse("2026-11-30")))
        assertEquals(1, PlanMath.daysInclusive(a, a))
        assertEquals(0, PlanMath.daysInclusive(a, a.minusDays(1)))
        assertEquals(27, PlanMath.daysRemaining(LocalDate.parse("2026-11-04"), LocalDate.parse("2026-11-30")))
        assertEquals(0, PlanMath.daysRemaining(LocalDate.parse("2026-12-04"), LocalDate.parse("2026-11-30")))
        assertNull(PlanMath.daysRemaining(a, null))
        assertEquals(4, PlanMath.requiredDaily(100, 25))
        assertNull(PlanMath.requiredDaily(100, 0))
        assertNull(PlanMath.requiredDaily(0, 10))
    }

    @Test fun historyDatesAndStatuses() {
        val start = LocalDate.parse("2026-11-01")
        val today = LocalDate.parse("2026-11-04")
        val h = PlanMath.historyDates(start, null, today, listOf(LocalDate.parse("2026-11-02")))
        assertEquals(listOf("2026-11-04", "2026-11-03", "2026-11-02", "2026-11-01"), h.map { it.toString() })
        // خطة منتهية: لا أيام بعد النهاية
        val ended = PlanMath.historyDates(start, LocalDate.parse("2026-11-02"), today, emptyList())
        assertEquals(listOf("2026-11-02", "2026-11-01"), ended.map { it.toString() })
        // بدء مستقبلي: لا أيام
        assertTrue(PlanMath.historyDates(LocalDate.parse("2026-12-01"), null, today, emptyList()).isEmpty())

        assertEquals(DayStatus.UNRECORDED, PlanMath.dayStatus(null))
        assertEquals(DayStatus.UNRECORDED, PlanMath.dayStatus(QuranPlanLogEntity("a", "p", "d")))
        assertEquals(DayStatus.PARTIAL, PlanMath.dayStatus(QuranPlanLogEntity("a", "p", "d", ayahsRead = 3)))
        assertEquals(DayStatus.MET, PlanMath.dayStatus(QuranPlanLogEntity("a", "p", "d", ayahsRead = 3, targetMet = true)))
        assertEquals(DayStatus.UNRECORDED, PlanMath.dayStatus(QuranPlanLogEntity("a", "p", "d", ayahsRead = 3, targetMet = true, deleted = true)))
    }

    // ---------------------------------------------------------- النصوص

    @Test fun labels() {
        val a = q.ayah(2, 25).id
        val b = q.ayah(2, 43).id
        assertEquals("من البقرة ٢٥ إلى ٤٣", m.rangeLabel(a, b))
        assertEquals("البقرة ٢٥", m.rangeLabel(a, a))
        val c = q.ayah(3, 20).id
        assertEquals("من البقرة ٢٥ إلى آل عمران ٢٠", m.rangeLabel(a, c))
        assertTrue(m.pagesLabel(a, b).startsWith("ص "))
        assertEquals("صفحة", PlanMath.unitText(QuranPlanUnit.PAGE, 1))
        assertEquals("صفحتان", PlanMath.unitText(QuranPlanUnit.PAGE, 2))
        assertEquals("٣ صفحات", PlanMath.unitText(QuranPlanUnit.PAGE, 3))
        assertEquals("١١ صفحة", PlanMath.unitText(QuranPlanUnit.PAGE, 11))
        assertEquals("٣ أجزاء", PlanMath.unitText(QuranPlanUnit.JUZ, 3))
    }

    @Test fun timeParsingAndFormat() {
        assertEquals(5 to 30, PlanMath.parseTime("05:30"))
        assertNull(PlanMath.parseTime("25:00"))
        assertNull(PlanMath.parseTime("abc"))
        assertNull(PlanMath.parseTime(null))
        assertEquals("05:05", PlanMath.formatTime(5, 5))
        assertEquals("٥:٣٠ ص", PlanFormat.time12("05:30"))
        assertEquals("١٢:٠٠ م", PlanFormat.time12("12:00"))
        assertEquals("١٢:١٠ ص", PlanFormat.time12("00:10"))
        assertEquals("123", PlanFormat.toLatinDigits("١٢٣"))
        assertEquals("الأربعاء ٤ نوفمبر ٢٠٢٦", PlanFormat.dateLong(LocalDate.parse("2026-11-04")))
    }

    // ---------------------------------------------------------- التذكيرات

    @Test fun reminderPlanning() {
        val today = LocalDate.parse("2026-11-04")
        val p = plan(QuranPlanUnit.PAGE, 1, 6236, daily = 2, startDate = "2026-11-01", endDate = "2026-11-30")
        val r = WirdReminders.buildReminders(m, p, null, today)
        assertEquals(3, r.size)
        assertEquals("2026-11-04", r[0].date.toString())
        // مقطع الغد يلي مقطع اليوم
        val d0 = m.todayPortion(p, 0)!!
        val d1 = m.todayPortion(p.copy(progressAyahId = d0.toAyah), d0.toAyah)!!
        assertEquals(m.rangeLabel(d0), r[0].portionLabel)
        assertEquals(m.rangeLabel(d1), r[1].portionLabel)
    }

    @Test fun reminderSkipsMetDayAndRespectsDates() {
        val today = LocalDate.parse("2026-11-04")
        val d0 = m.todayPortion(plan(daily = 2), 0)!!
        val done = plan(daily = 2, progress = d0.toAyah, endDate = "2026-11-05")
        val log = QuranPlanLogEntity("p_2026-11-04", "p", "2026-11-04", ayahsRead = d0.toAyah, targetMet = true)
        val r = WirdReminders.buildReminders(m, done, log, today)
        // اليوم محقق فلا تذكير، وغدًا آخر يوم في المدة، وبعده لا شيء
        assertEquals(listOf("2026-11-05"), r.map { it.date.toString() })
        assertEquals(m.rangeLabel(m.todayPortion(done, d0.toAyah)!!), r[0].portionLabel)

        // بدء مستقبلي: لا تذكير قبل البداية، وأول مقطع هو مقطع يوم البداية
        val future = plan(daily = 2, startDate = "2026-11-06")
        val rf = WirdReminders.buildReminders(m, future, null, today)
        assertEquals(listOf("2026-11-06"), rf.map { it.date.toString() })
        assertEquals(m.rangeLabel(m.todayPortion(future, 0)!!), rf[0].portionLabel)
    }

    @Test fun reminderNothingWhenComplete() {
        val today = LocalDate.parse("2026-11-04")
        val finished = plan(QuranPlanUnit.PAGE, 1, 7, progress = 7)
        assertTrue(WirdReminders.buildReminders(m, finished, null, today).isEmpty())
    }
}
