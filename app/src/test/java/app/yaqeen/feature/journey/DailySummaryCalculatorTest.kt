package app.yaqeen.feature.journey

import app.yaqeen.data.local.AdhkarProgressEntity
import app.yaqeen.data.local.DailySummaryEntity
import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.QuranDayLogEntity
import app.yaqeen.data.local.QuranPlanLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailySummaryCalculatorTest {
    private val day = LocalDate.of(2026, 11, 4)
    private val d = day.toString()
    private val index = AdhkarItemIndex(
        mapOf(
            "morning" to setOf("m1", "m2"),
            "evening" to setOf("e1"),
            "sleep" to setOf("s1", "s2", "s3"),
        ),
    )

    private fun prayer(p: String, status: String, deleted: Boolean = false, updatedAt: Long = 1, date: String = d) =
        PrayerLogEntity("${date}_$p", date, p, status, deleted = deleted, updatedAt = updatedAt)

    private fun dhikr(id: String, cat: String, done: Boolean, count: Int = 1, target: Int = 1, deleted: Boolean = false) =
        AdhkarProgressEntity("${d}_$id", d, cat, id, count, target, done, deleted = deleted)

    private fun calc(inputs: DayInputs = DayInputs(day), tasks: TaskDayCount? = null) =
        DailySummaryCalculator.compute(inputs, index, tasks)

    @Test fun emptyDayIsAllZeroAndNotMeaningful() {
        val s = calc()
        assertEquals(d, s.id)
        assertEquals(d, s.date)
        assertEquals(0, s.prayersRecorded)
        assertFalse(s.adhkarMorningDone)
        assertFalse(s.isMeaningful())
        assertFalse(s.hasWorshipActivity())
        assertEquals(0, s.worshipUnits())
    }

    @Test fun countsPrayerStatuses() {
        val s = calc(DayInputs(day, prayers = listOf(
            prayer("FAJR", "MOSQUE"), prayer("DHUHR", "HOME"), prayer("ASR", "NOT_PRAYED"),
        )))
        assertEquals(3, s.prayersRecorded)
        assertEquals(1, s.prayersMosque)
        assertEquals(1, s.prayersHome)
        assertEquals(1, s.prayersNotPrayed)
        assertEquals(2, s.prayersPrayed)
        assertTrue(s.hasWorshipActivity())
    }

    @Test fun notPrayedAloneIsNotWorshipActivityButIsMeaningful() {
        val s = calc(DayInputs(day, prayers = listOf(prayer("FAJR", "NOT_PRAYED"))))
        assertFalse(s.hasWorshipActivity())
        assertTrue(s.isMeaningful())
        assertEquals(0, s.worshipUnits())
    }

    @Test fun deletedRowsAreExcluded() {
        val s = calc(DayInputs(
            day,
            prayers = listOf(prayer("FAJR", "MOSQUE", deleted = true)),
            rawatib = listOf(RawatibLogEntity("${d}_FAJR_BEFORE", d, "FAJR_BEFORE", true, 2, deleted = true)),
            adhkar = listOf(dhikr("e1", "evening", true, deleted = true)),
            quranDay = QuranDayLogEntity(d, d, pagesRead = 3, deleted = true),
            planLogs = listOf(QuranPlanLogEntity("p_$d", "p", d, targetMet = true, deleted = true)),
        ))
        assertEquals(0, s.prayersRecorded)
        assertEquals(0, s.rawatibDone)
        assertFalse(s.adhkarEveningDone)
        assertEquals(0, s.quranPages)
        assertFalse(s.quranPlanTargetMet)
        assertFalse(s.isMeaningful())
    }

    @Test fun duplicatePrayerRowsCountOnceLatestWins() {
        val s = calc(DayInputs(day, prayers = listOf(
            prayer("FAJR", "NOT_PRAYED", updatedAt = 1), prayer("FAJR", "MOSQUE", updatedAt = 2),
        )))
        assertEquals(1, s.prayersRecorded)
        assertEquals(1, s.prayersMosque)
        assertEquals(0, s.prayersNotPrayed)
    }

    @Test fun rowsOfOtherDatesAndUnknownPrayersAreIgnored() {
        val s = calc(DayInputs(day, prayers = listOf(
            prayer("FAJR", "MOSQUE", date = "2026-11-03"), prayer("TAHAJJUD", "HOME"), prayer("ASR", "WEIRD"),
        )))
        assertEquals(0, s.prayersMosque)
        assertEquals(0, s.prayersHome)
        assertEquals(0, s.prayersRecorded)
    }

    @Test fun rawatibCountsDoneRowsAndRakaat() {
        val s = calc(DayInputs(day, rawatib = listOf(
            RawatibLogEntity("${d}_FAJR_BEFORE", d, "FAJR_BEFORE", true, 2),
            RawatibLogEntity("${d}_DHUHR_BEFORE", d, "DHUHR_BEFORE", true, 4),
            RawatibLogEntity("${d}_ISHA_AFTER", d, "ISHA_AFTER", false, 2),
            RawatibLogEntity("${d}_custom:x", d, "custom:x", true, 3),
        )))
        assertEquals(3, s.rawatibDone)
        assertEquals(9, s.rawatibRakaat)
    }

    @Test fun adhkarKeptOnlyWhenWholeCategoryCompleted() {
        val partial = calc(DayInputs(day, adhkar = listOf(dhikr("m1", "morning", true), dhikr("m2", "morning", false, count = 0))))
        assertFalse(partial.adhkarMorningDone)
        assertEquals(1, partial.adhkarItemsCompleted)

        val full = calc(DayInputs(day, adhkar = listOf(dhikr("m1", "morning", true), dhikr("m2", "morning", true), dhikr("e1", "evening", true))))
        assertTrue(full.adhkarMorningDone)
        assertTrue(full.adhkarEveningDone)
        assertFalse(full.adhkarSleepDone)
        assertEquals(3, full.adhkarItemsCompleted)
    }

    @Test fun adhkarCompletedByCountReachingTarget() {
        val s = calc(DayInputs(day, adhkar = listOf(dhikr("e1", "evening", false, count = 33, target = 33))))
        assertTrue(s.adhkarEveningDone)
    }

    @Test fun unknownCategoryOrEmptyCatalogIsNeverKept() {
        val s = DailySummaryCalculator.compute(
            DayInputs(day, adhkar = listOf(dhikr("m1", "morning", true))),
            AdhkarItemIndex.EMPTY, null,
        )
        assertFalse(s.adhkarMorningDone)
        assertEquals(1, s.adhkarItemsCompleted)
    }

    @Test fun quranFromDayLogAndPlanLogs() {
        val s = calc(DayInputs(
            day,
            quranDay = QuranDayLogEntity(d, d, pagesRead = 5, ayahsRead = 80),
            planLogs = listOf(QuranPlanLogEntity("a_$d", "a", d, targetMet = true), QuranPlanLogEntity("b_$d", "b", d, targetMet = false)),
        ))
        assertEquals(5, s.quranPages)
        assertEquals(80, s.quranAyahs)
        assertFalse("ورد واحد لم يبلغ مقداره", s.quranPlanTargetMet)

        val all = calc(DayInputs(day, planLogs = listOf(QuranPlanLogEntity("a_$d", "a", d, pagesRead = 2, ayahsRead = 20, targetMet = true))))
        assertTrue(all.quranPlanTargetMet)
        assertEquals("يُستعمل سجل الورد إن غاب سجل اليوم", 2, all.quranPages)
    }

    @Test fun tasksComeFromEngineCounts() {
        val s = calc(tasks = TaskDayCount(5, 2))
        assertEquals(5, s.tasksTotal)
        assertEquals(2, s.tasksDone)
        assertTrue("إنجاز مهمة وحده معنى", s.isMeaningful())
        assertFalse("المهام وحدها ليست عبادة", s.hasWorshipActivity())
        assertFalse(calc(tasks = TaskDayCount(5, 0)).isMeaningful())
    }

    @Test fun worshipUnitsAreCappedAndCountedByArea() {
        val s = DailySummaryEntity(
            id = d, date = d, prayersMosque = 4, prayersHome = 3, rawatibDone = 7,
            adhkarMorningDone = true, adhkarEveningDone = true, adhkarSleepDone = true, quranPages = 1,
        )
        assertEquals(JourneyConfig.MAX_DAILY_UNITS, s.worshipUnits())
    }

    @Test fun sameValuesIgnoresSyncMeta() {
        val a = DailySummaryEntity(id = d, date = d, prayersMosque = 2, updatedAt = 5, deviceId = "A", opId = "x", dirty = true)
        val b = a.copy(updatedAt = 99, deviceId = "B", opId = "y", dirty = false, deleted = true)
        assertTrue(a.sameValuesAs(b))
        assertFalse(a.sameValuesAs(a.copy(prayersMosque = 3)))
        assertFalse(a.sameValuesAs(a.copy(tasksTotal = 1)))
    }
}
