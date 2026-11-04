package app.yaqeen.feature.journey

import app.yaqeen.data.local.DailySummaryEntity
import app.yaqeen.data.local.QuranPlanEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

private val TODAY: LocalDate = LocalDate.of(2026, 11, 4) // الأربعاء

private fun s(
    date: LocalDate,
    mosque: Int = 0, home: Int = 0, notPrayed: Int = 0,
    rawatib: Int = 0, rakaat: Int = 0,
    morning: Boolean = false, evening: Boolean = false, sleep: Boolean = false, items: Int = 0,
    pages: Int = 0, ayahs: Int = 0, planMet: Boolean = false,
    tasksTotal: Int = 0, tasksDone: Int = 0,
    deleted: Boolean = false,
) = DailySummaryEntity(
    id = date.toString(), date = date.toString(),
    prayersRecorded = mosque + home + notPrayed, prayersMosque = mosque, prayersHome = home, prayersNotPrayed = notPrayed,
    rawatibDone = rawatib, rawatibRakaat = rakaat,
    adhkarMorningDone = morning, adhkarEveningDone = evening, adhkarSleepDone = sleep, adhkarItemsCompleted = items,
    quranPages = pages, quranAyahs = ayahs, quranPlanTargetMet = planMet,
    tasksTotal = tasksTotal, tasksDone = tasksDone, deleted = deleted,
)

private fun active(date: LocalDate) = s(date, mosque = 1)

class PeriodRangeTest {
    @Test fun lastNDaysIsInclusiveOfToday() {
        val r = PeriodRange.last(TODAY, 7)
        assertEquals(LocalDate.of(2026, 10, 29), r.from)
        assertEquals(TODAY, r.to)
        assertEquals(7, r.days)
        assertTrue(TODAY in r)
        assertTrue(LocalDate.of(2026, 10, 29) in r)
        assertFalse(LocalDate.of(2026, 10, 28) in r)
        assertFalse(TODAY.plusDays(1) in r)
    }

    @Test fun previousPeriodIsAdjacentAndSameLength() {
        val r = PeriodRange.last(TODAY, 30)
        val p = r.previous()
        assertEquals(30, p.days)
        assertEquals(r.from.minusDays(1), p.to)
        assertEquals(r.from.minusDays(30), p.from)
    }

    @Test fun crossesYearAndLeapDayWithoutDriftingDst() {
        val r = PeriodRange.last(LocalDate.of(2024, 3, 1), 3)
        assertEquals(LocalDate.of(2024, 2, 28), r.from) // 2024 كبيسة
        assertEquals(3, r.days)
        val y = PeriodRange.last(LocalDate.of(2026, 1, 2), 5)
        assertEquals(LocalDate.of(2025, 12, 29), y.from)
        // الأيام عبر تغيّر التوقيت الصيفي (مارس) تبقى 7
        assertEquals(7, PeriodRange(LocalDate.of(2026, 3, 25), LocalDate.of(2026, 3, 31)).days)
    }
}

class ArabicNounTest {
    private val ind: (Int) -> String = { app.yaqeen.core.ArabicText.indic(it) }

    @Test fun agreementForms() {
        assertEquals("يوم واحد", ArabicNoun.DAY.count(1, ind))
        assertEquals("يومين", ArabicNoun.DAY.count(2, ind))
        assertEquals("٣ أيام", ArabicNoun.DAY.count(3, ind))
        assertEquals("١٠ أيام", ArabicNoun.DAY.count(10, ind))
        assertEquals("١١ يومًا", ArabicNoun.DAY.count(11, ind))
        assertEquals("٣٠ يومًا", ArabicNoun.DAY.count(30, ind))
        assertEquals("٥ ركعات", ArabicNoun.RAKAA.count(5, ind))
        assertEquals("١٢ ركعة", ArabicNoun.RAKAA.count(12, ind))
        assertEquals("٣ مهام", ArabicNoun.TASK.count(3, ind))
        assertEquals("لا شيء", ArabicNoun.DAY.count(0, ind))
    }
}

class StreakCalculatorTest {
    private fun days(vararg offsets: Int) = offsets.map { TODAY.minusDays(it.toLong()) }.toSet()

    @Test fun emptyHasNoStreak() {
        val st = StreakCalculator.compute(emptySet(), TODAY)
        assertEquals(0, st.current)
        assertEquals(0, st.best)
        assertNull(st.bestStart)
    }

    @Test fun singleDayToday() {
        val st = StreakCalculator.compute(days(0), TODAY)
        assertEquals(1, st.current)
        assertEquals(1, st.best)
        assertEquals(TODAY, st.bestStart)
        assertEquals(TODAY, st.bestEnd)
    }

    @Test fun todayEmptyDoesNotBreakYesterdaysStreak() {
        val st = StreakCalculator.compute(days(1, 2, 3), TODAY)
        assertEquals(3, st.current)
        assertEquals(3, st.best)
    }

    @Test fun gapOfAWholeDayBreaksCurrent() {
        val st = StreakCalculator.compute(days(2, 3, 4), TODAY)
        assertEquals(0, st.current)
        assertEquals(3, st.best)
    }

    @Test fun bestIsLongestRunAndTieGoesToMostRecent() {
        // سلسلتان بطول 3: قبل 10..8 و قبل 3..1
        val st = StreakCalculator.compute(days(10, 9, 8, 3, 2, 1), TODAY)
        assertEquals(3, st.best)
        assertEquals(TODAY.minusDays(3), st.bestStart)
        assertEquals(TODAY.minusDays(1), st.bestEnd)
        assertEquals(3, st.current)
    }

    @Test fun longerOldRunBeatsShortCurrent() {
        val st = StreakCalculator.compute(days(0, 1, 20, 19, 18, 17, 16), TODAY)
        assertEquals(2, st.current)
        assertEquals(5, st.best)
        assertEquals(TODAY.minusDays(20), st.bestStart)
    }

    @Test fun futureDatesIgnored() {
        val st = StreakCalculator.compute(setOf(TODAY, TODAY.plusDays(1), TODAY.plusDays(2)), TODAY)
        assertEquals(1, st.best)
        assertEquals(1, st.current)
    }

    @Test fun monthBoundary() {
        val today = LocalDate.of(2026, 3, 2)
        val set = setOf(LocalDate.of(2026, 2, 27), LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 1), today)
        assertEquals(4, StreakCalculator.compute(set, today).current)
    }

    @Test fun activeDaysUsesWorshipOnlyAndSkipsDeleted() {
        val rows = listOf(
            active(TODAY),
            s(TODAY.minusDays(1), notPrayed = 1),                 // "لم أصلِّ بعد" وحده ليس نشاطًا
            s(TODAY.minusDays(2), tasksTotal = 3, tasksDone = 3), // مهام فقط
            s(TODAY.minusDays(3), items = 1),
            s(TODAY.minusDays(4), pages = 2),
            s(TODAY.minusDays(5), mosque = 1, deleted = true),
        )
        val a = StreakCalculator.activeDays(rows)
        assertEquals(setOf(TODAY, TODAY.minusDays(3), TODAY.minusDays(4)), a)
    }
}

class HeatmapBuilderTest {
    @Test fun levelsByShareOfDailyUnits() {
        assertEquals(0, HeatmapBuilder.level(null))
        assertEquals(0, HeatmapBuilder.level(s(TODAY)))
        assertEquals(0, HeatmapBuilder.level(s(TODAY, notPrayed = 2)))
        assertEquals(1, HeatmapBuilder.level(s(TODAY, items = 1)))      // نشاط بلا وحدات كاملة = 1
        assertEquals(1, HeatmapBuilder.level(s(TODAY, mosque = 3)))     // 3/14
        assertEquals(2, HeatmapBuilder.level(s(TODAY, mosque = 5)))     // 5/14
        assertEquals(3, HeatmapBuilder.level(s(TODAY, mosque = 5, rawatib = 5)))  // 10/14
        assertEquals(4, HeatmapBuilder.level(s(TODAY, mosque = 5, rawatib = 5, morning = true, evening = true, pages = 1))) // 13/14
    }

    @Test fun weekStartsOnSaturday() {
        assertEquals(DayOfWeek.SATURDAY, HeatmapBuilder.startOfWeek(TODAY).dayOfWeek)
        assertEquals(LocalDate.of(2026, 10, 31), HeatmapBuilder.startOfWeek(TODAY)) // الأربعاء 4 نوفمبر -> السبت 31 أكتوبر
        assertEquals(LocalDate.of(2026, 10, 31), HeatmapBuilder.startOfWeek(LocalDate.of(2026, 10, 31)))
        assertEquals(LocalDate.of(2026, 10, 31), HeatmapBuilder.startOfWeek(LocalDate.of(2026, 11, 6))) // الجمعة
    }

    @Test fun gridShapeAndFutureCells() {
        val g = HeatmapBuilder.build(listOf(active(TODAY)), TODAY)
        assertEquals(13, g.weeks.size)
        g.weeks.forEach { assertEquals(7, it.size) }
        // أول عمود يبدأ بسبت قبل 12 أسبوعًا من سبت أسبوع اليوم
        assertEquals(LocalDate.of(2026, 10, 31).minusWeeks(12), g.firstDate)
        // آخر أسبوع: السبت..الأربعاء موجودة، الخميس والجمعة مستقبلية
        val last = g.weeks.last()
        assertNotNull(last[0]); assertNotNull(last[4])
        assertNull(last[5]); assertNull(last[6])
        assertEquals(1, last[4]!!.level) // اليوم نفسه
        assertEquals(TODAY, last[4]!!.date)
    }

    @Test fun deletedSummariesDoNotColorCells() {
        val g = HeatmapBuilder.build(listOf(s(TODAY, mosque = 5, deleted = true)), TODAY)
        assertEquals(0, g.weeks.last()[4]!!.level)
    }

    @Test fun cellsAreConsecutiveDays() {
        val g = HeatmapBuilder.build(emptyList(), TODAY)
        val dates = g.weeks.flatten().filterNotNull().map { it.date }
        for (i in 1 until dates.size) assertEquals(dates[i - 1].plusDays(1), dates[i])
    }
}

class PeriodAggregatorTest {
    private val r7 = PeriodRange.last(TODAY, 7)

    @Test fun emptyDataGivesZeros() {
        assertEquals(0, PeriodAggregator.quran(emptyList(), r7).daysRead)
        assertEquals(0, PeriodAggregator.adhkar(emptyList(), r7).recordedDays)
        assertEquals(0, PeriodAggregator.prayers(emptyList(), r7, null, TODAY).prayed)
        assertEquals(0, PeriodAggregator.rawatib(emptyList(), r7).rakaat)
        assertEquals(0, PeriodAggregator.tasks(emptyList(), r7).total)
        assertNull(PeriodAggregator.tasks(emptyList(), r7).completion)
        assertNull(PeriodAggregator.trackingStart(emptyList()))
    }

    @Test fun periodBoundariesAreInclusive() {
        val rows = listOf(
            s(r7.from.minusDays(1), pages = 9),   // خارج
            s(r7.from, pages = 1),                // أول يوم
            s(TODAY, pages = 2),                  // آخر يوم
            s(TODAY.plusDays(1), pages = 7),      // مستقبل (خارج)
        )
        val q = PeriodAggregator.quran(rows, r7)
        assertEquals(2, q.daysRead)
        assertEquals(3, q.pages)
    }

    @Test fun deletedAndDuplicateRowsHandled() {
        val rows = listOf(s(TODAY, pages = 5, deleted = true), s(TODAY, pages = 2), s(TODAY, pages = 99))
        val q = PeriodAggregator.quran(rows, r7)
        assertEquals(1, q.daysRead) // distinct by date بعد استبعاد المحذوف
    }

    @Test fun quranJuzEquivalent() {
        val q = PeriodAggregator.quran(listOf(s(TODAY, pages = 20, ayahs = 300), s(TODAY.minusDays(1), pages = 10)), r7)
        assertEquals(30, q.pages)
        assertEquals(300, q.ayahs)
        assertEquals(30 / (604 / 30.0), q.juzEquivalent, 1e-9)
    }

    @Test fun wirdCommitmentUsesOnlyActivePlansAndDaysWithLogs() {
        val logs = listOf(
            QuranPlanLogEntity("a_1", "a", TODAY.toString(), targetMet = true),
            QuranPlanLogEntity("a_2", "a", TODAY.minusDays(1).toString(), targetMet = false),
            QuranPlanLogEntity("a_3", "a", TODAY.minusDays(2).toString(), targetMet = true),
            QuranPlanLogEntity("b_1", "b", TODAY.minusDays(2).toString(), targetMet = false), // ورد غير نشط
            QuranPlanLogEntity("a_old", "a", TODAY.minusDays(20).toString(), targetMet = true), // خارج الفترة
            QuranPlanLogEntity("a_del", "a", TODAY.minusDays(3).toString(), targetMet = true, deleted = true),
        )
        val w = PeriodAggregator.wird(logs, setOf("a"), r7)
        assertEquals(3, w.daysWithLog)
        assertEquals(2, w.daysTargetMet)
        assertEquals(2.0 / 3, w.ratio!!, 1e-9)
    }

    @Test fun wirdWithTwoPlansSameDayNeedsBothMet() {
        val d = TODAY.toString()
        val logs = listOf(
            QuranPlanLogEntity("a_1", "a", d, targetMet = true),
            QuranPlanLogEntity("b_1", "b", d, targetMet = false),
        )
        val w = PeriodAggregator.wird(logs, setOf("a", "b"), r7)
        assertEquals(1, w.daysWithLog)
        assertEquals(0, w.daysTargetMet)
    }

    @Test fun wirdWithoutLogsHasNullRatioNotZeroPercent() {
        val w = PeriodAggregator.wird(emptyList(), setOf("a"), r7)
        assertEquals(0, w.daysWithLog)
        assertNull(w.ratio)
    }

    @Test fun plansOverviewAndRemainingPages() {
        fun plan(id: String, status: String, start: Int, end: Int, progress: Int, deleted: Boolean = false) = QuranPlanEntity(
            id = id, name = id, startDate = "2026-01-01", unit = "PAGE", startAyahId = start, endAyahId = end,
            dailyAmount = 1, progressAyahId = progress, status = status, deleted = deleted,
        )
        val pageOf = { ayah: Int -> (ayah - 1) / 10 + 1 } // 10 آيات لكل صفحة
        val plans = listOf(
            plan("a", "ACTIVE", 1, 100, 0),     // صفحات 1..10 كلها متبقية = 10
            plan("b", "ACTIVE", 1, 100, 55),    // التالية 56 (صفحة 6) -> 6..10 = 5
            plan("c", "ACTIVE", 1, 100, 100),   // مكتملة فعليًا = 0
            plan("d", "PAUSED", 1, 100, 0),
            plan("e", "COMPLETED", 1, 100, 100),
            plan("f", "ACTIVE", 1, 100, 0, deleted = true),
        )
        val o = PeriodAggregator.plans(plans, pageOf)
        assertEquals(3, o.active)
        assertEquals(1, o.paused)
        assertEquals(1, o.completed)
        assertEquals(15, o.activeRemainingPages)
        assertNull(PeriodAggregator.plans(plans, null).activeRemainingPages)
        assertNull(PeriodAggregator.plans(emptyList(), pageOf).activeRemainingPages)
    }

    @Test fun adhkarDenominatorIsRecordedDaysOnly() {
        val rows = listOf(
            s(TODAY, morning = true, items = 10),
            s(TODAY.minusDays(1), morning = true, evening = true, items = 12),
            s(TODAY.minusDays(2), items = 3),
            s(TODAY.minusDays(3), mosque = 5),   // لا أذكار مسجّلة: خارج المقام
        )
        val a = PeriodAggregator.adhkar(rows, r7)
        assertEquals(3, a.recordedDays)
        assertEquals(2, a.morningDays)
        assertEquals(1, a.eveningDays)
        assertEquals(0, a.sleepDays)
        assertEquals(25, a.itemsCompleted)
        assertEquals(2.0 / 3, a.rate(a.morningDays)!!, 1e-9)
        assertNull(PeriodAggregator.adhkar(emptyList(), r7).rate(0))
    }

    @Test fun prayersTotalsAndShares() {
        val rows = listOf(
            s(TODAY.minusDays(1), mosque = 3, home = 1, notPrayed = 1),
            s(TODAY.minusDays(2), mosque = 1, home = 3),
        )
        val p = PeriodAggregator.prayers(rows, r7, TODAY.minusDays(2), TODAY)
        assertEquals(8, p.prayed)
        assertEquals(4, p.mosque)
        assertEquals(4, p.home)
        assertEquals(1, p.markedNotYet)
        assertEquals(2, p.daysRecorded)
        assertEquals(0.5, p.mosqueShare!!, 1e-9)
        assertEquals(0.5, p.homeShare!!, 1e-9)
    }

    @Test fun prayersWithNothingPrayedHasNullShares() {
        val p = PeriodAggregator.prayers(listOf(s(TODAY, notPrayed = 2)), r7, TODAY, TODAY)
        assertEquals(0, p.prayed)
        assertNull(p.mosqueShare)
        assertEquals(2, p.markedNotYet)
    }

    @Test fun unrecordedSlotsCountFromTrackingStartAndExcludeToday() {
        // بدأ التسجيل قبل 3 أيام (أمس-2 .. أمس = 3 أيام مكتملة قبل اليوم)
        val start = TODAY.minusDays(3)
        val rows = listOf(
            s(start, mosque = 5),                 // 5 مسجّلة
            s(TODAY.minusDays(1), home = 2),      // 2 مسجّلة
            s(TODAY, mosque = 5),                 // اليوم لا يدخل في الخانات
        )
        val p = PeriodAggregator.prayers(rows, r7, start, TODAY)
        // الأيام المكتملة: start, -2, -1 = 3 أيام × 5 = 15 خانة، مسجّل 7 => 8 غير مسجّل
        assertEquals(8, p.unrecordedSlots)
    }

    @Test fun unrecordedSlotsZeroWhenNoTrackingStart() {
        assertEquals(0, PeriodAggregator.prayers(emptyList(), r7, null, TODAY).unrecordedSlots)
    }

    @Test fun unrecordedSlotsNeverNegativeAndSkipDaysBeforePeriod() {
        val tracking = TODAY.minusDays(100)
        val p = PeriodAggregator.prayers(listOf(s(TODAY.minusDays(1), mosque = 5)), r7, tracking, TODAY)
        // الفترة 7 أيام: المكتمل منها 6 أيام (من -6 إلى -1) = 30 خانة، المسجّل 5
        assertEquals(25, p.unrecordedSlots)
    }

    @Test fun rawatibConsistencyUsesActiveDays() {
        val rows = listOf(
            s(TODAY, mosque = 5, rawatib = 3, rakaat = 8),
            s(TODAY.minusDays(1), mosque = 5),
            s(TODAY.minusDays(2), mosque = 5, rawatib = 5, rakaat = 12),
            s(TODAY.minusDays(3), notPrayed = 1), // ليس يومًا نشطًا
        )
        val r = PeriodAggregator.rawatib(rows, r7)
        assertEquals(20, r.rakaat)
        assertEquals(2, r.daysDone)
        assertEquals(3, r.activeDays)
        assertEquals(2.0 / 3, r.consistency!!, 1e-9)
        assertNull(RawatibStats(0, 0, 0).consistency)
    }

    @Test fun tasksTotalsDayAndBestWindow() {
        val r30 = PeriodRange.last(TODAY, 30)
        val rows = listOf(
            s(TODAY, mosque = 1, tasksTotal = 4, tasksDone = 4),
            s(TODAY.minusDays(1), mosque = 1, tasksTotal = 4, tasksDone = 3),
            s(TODAY.minusDays(2), mosque = 1, tasksTotal = 4, tasksDone = 4),
            // أسبوع أضعف قبل 20 يومًا
            s(TODAY.minusDays(20), mosque = 1, tasksTotal = 4, tasksDone = 1),
            s(TODAY.minusDays(21), mosque = 1, tasksTotal = 4, tasksDone = 0),
            s(TODAY.minusDays(22), mosque = 1, tasksTotal = 4, tasksDone = 2),
            s(TODAY.minusDays(10), mosque = 1), // لا مهام
        )
        val t = PeriodAggregator.tasks(rows, r30)
        assertEquals(24, t.total)
        assertEquals(14, t.done)
        assertEquals(6, t.daysCounted)
        assertEquals(14.0 / 24, t.completion!!, 1e-9)
        val w = t.bestWindow
        assertNotNull(w)
        assertEquals(TODAY.minusDays(2), w!!.from)
        assertEquals(TODAY, w.to)
        assertEquals(11, w.done)
        assertEquals(12, w.total)
        assertEquals(TODAY, t.bestDay!!.to) // 4/4 والأحدث
    }

    @Test fun shortPeriodHasNoBestWindowButHasBestDay() {
        val rows = listOf(s(TODAY, mosque = 1, tasksTotal = 2, tasksDone = 1), s(TODAY.minusDays(1), mosque = 1, tasksTotal = 2, tasksDone = 2))
        val t = PeriodAggregator.tasks(rows, r7)
        assertNull(t.bestWindow)
        assertEquals(TODAY.minusDays(1), t.bestDay!!.to)
    }

    @Test fun bestWindowNeedsEnoughTaskDays() {
        val rows = listOf(s(TODAY, tasksTotal = 2, tasksDone = 2), s(TODAY.minusDays(1), tasksTotal = 2, tasksDone = 2))
        assertNull(BestPeriodFinder.bestWindow(rows, 7))
        assertNull(BestPeriodFinder.bestWindow(emptyList(), 7))
    }

    @Test fun bestWindowTieGoesToMoreDoneThenRecent() {
        // نافذتان بنسبة 100%: الأولى 6/6 والثانية 9/9 => الأكثر إنجازًا
        val rows = listOf(20, 19, 18).map { s(TODAY.minusDays(it.toLong()), tasksTotal = 2, tasksDone = 2) } +
            listOf(2, 1, 0).map { s(TODAY.minusDays(it.toLong()), tasksTotal = 3, tasksDone = 3) }
        val w = BestPeriodFinder.bestWindow(rows.sortedBy { it.date }, 7)!!
        assertEquals(9, w.done)
        assertEquals(TODAY, w.to)
    }

    @Test fun trackingStartIsEarliestMeaningfulDay() {
        val rows = listOf(s(TODAY.minusDays(40)), s(TODAY.minusDays(30), tasksDone = 1, tasksTotal = 1), s(TODAY.minusDays(5), mosque = 1))
        assertEquals(TODAY.minusDays(30), PeriodAggregator.trackingStart(rows))
    }
}

class PeriodComparerTest {
    @Test fun noPreviousDataIsFlagged() {
        val c = PeriodComparer.compare(listOf(s(TODAY, mosque = 3)), PeriodRange.last(TODAY, 7))
        assertFalse(c.hasPrevious)
    }

    @Test fun increaseSameAndDecreaseWordingIsCalm() {
        val cur = PeriodRange.last(TODAY, 7)
        val prev = cur.previous()
        val rows = listOf(
            // الحالية: قراءة 3 أيام، صلوات 6
            s(TODAY, pages = 1, mosque = 2), s(TODAY.minusDays(1), pages = 1, mosque = 2), s(TODAY.minusDays(2), pages = 1, mosque = 2),
            // السابقة: قراءة يوم واحد، صلوات 8
            s(prev.to, pages = 1, mosque = 4), s(prev.to.minusDays(1), mosque = 4),
        )
        val c = PeriodComparer.compare(rows, cur)
        assertTrue(c.hasPrevious)
        val q = c.metrics.first { it.metric == CompareMetric.QURAN_DAYS }
        assertEquals(3, q.current); assertEquals(1, q.previous)
        assertEquals(Trend.UP, q.trend)
        assertEquals("زاد عن الفترة السابقة بـ يومين", q.phrase())

        val p = c.metrics.first { it.metric == CompareMetric.PRAYERS }
        assertEquals(Trend.DOWN, p.trend)
        assertEquals("أقل بـ صلاتين من الفترة السابقة، ولكل فترة ظروفها", p.phrase())
        assertFalse(p.phrase().contains("تقصير"))

        val m = c.metrics.first { it.metric == CompareMetric.MORNING }
        assertEquals(Trend.SAME, m.trend)
        assertEquals("مماثل للفترة السابقة", m.phrase())
    }

    @Test fun increaseOfThreeDays() {
        val mc = MetricComparison(CompareMetric.QURAN_DAYS, 5, 2)
        assertEquals("زاد عن الفترة السابقة بـ ٣ أيام", mc.phrase { app.yaqeen.core.ArabicText.indic(it) })
    }

    @Test fun previousPeriodBeyondRangeStillUsesAvailableRows() {
        val cur = PeriodRange.last(TODAY, 90)
        val rows = listOf(s(TODAY, pages = 1), s(cur.from.minusDays(1), pages = 1), s(cur.from.minusDays(2), pages = 1))
        val c = PeriodComparer.compare(rows, cur)
        assertTrue(c.hasPrevious)
        assertEquals(2, c.metrics.first { it.metric == CompareMetric.QURAN_DAYS }.previous)
    }
}

class ChartBuilderTest {
    @Test fun dailySeriesOldestFirstWithUnrecordedAsNull() {
        val rows = listOf(s(TODAY, mosque = 5), s(TODAY.minusDays(2), items = 1), s(TODAY.minusDays(1), notPrayed = 2))
        val pts = ChartBuilder.daily(rows, TODAY, 7)
        assertEquals(7, pts.size)
        assertEquals(TODAY.minusDays(6), pts.first().date)
        assertEquals(TODAY, pts.last().date)
        assertEquals(5, pts.last().units)
        assertNull("لا نشاط = غير مسجّل لا صفر", pts[5].units)
        assertEquals(0, pts[4].units) // نشاط (ذكر) بلا وحدة كاملة = 0 وحدة لكنه مسجّل
        assertNull(pts[0].units)
    }
}

class EncouragementTest {
    private val forbidden = listOf("مقصّر", "مقصر", "فاشل", "كسول", "أسوأ", "متأخر", "رتبة", "نقاط", "تفوّق", "منافس")

    @Test fun neverShamingNorGamified() {
        val phrases = mutableSetOf<String>()
        for (any in listOf(false, true)) for (active in listOf(0, 3, 6)) for (streak in listOf(0, 5)) for (seed in 0L..5L) {
            phrases += Encouragement.forJourney(active, 7, streak, any, seed)
        }
        assertTrue(phrases.size >= 8)
        phrases.forEach { p -> forbidden.forEach { assertFalse("$p تحوي $it", p.contains(it)) } }
    }

    @Test fun deterministicPerSeedAndCoversCases() {
        assertEquals(Encouragement.forJourney(2, 30, 0, true, 7), Encouragement.forJourney(2, 30, 0, true, 7))
        assertTrue(Encouragement.forJourney(0, 30, 0, false, 0).contains("تبدأ"))
        assertTrue(Encouragement.forJourney(0, 30, 0, true, 0).isNotBlank())
        // seed سالب لا يرمي استثناءً
        assertTrue(Encouragement.forJourney(5, 7, 4, true, -3).isNotBlank())
    }
}

class JourneyFormatTest {
    @Test fun formats() {
        assertEquals("٧٥٪", JourneyFormat.percent(0.75))
        assertEquals("٠٪", JourneyFormat.percent(0.0))
        assertEquals("١٠٠٪", JourneyFormat.percent(1.0))
        assertEquals("٣٫٢", JourneyFormat.decimal1(3.24))
        assertEquals("٤ نوفمبر", JourneyFormat.shortDate(TODAY))
        assertEquals("٤ نوفمبر", JourneyFormat.range(TODAY, TODAY))
        assertEquals("٢٩ أكتوبر إلى ٤ نوفمبر", JourneyFormat.range(LocalDate.of(2026, 10, 29), TODAY))
        assertEquals("أربعاء", JourneyFormat.shortDayName(TODAY))
    }
}

class DayTickerTest {
    @Test fun millisUntilNextDayIsPositiveAndWithinADay() {
        val zone = java.time.ZoneId.of("Asia/Riyadh")
        val noon = LocalDate.of(2026, 11, 4).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(12 * 3600_000L + 1_000L, DayTicker.millisUntilNextDay(noon, zone))
    }
}
