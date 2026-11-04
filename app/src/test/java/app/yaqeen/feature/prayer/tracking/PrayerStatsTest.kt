package app.yaqeen.feature.prayer.tracking

import app.yaqeen.data.local.PrayerLogEntity
import app.yaqeen.data.local.RawatibLogEntity
import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus
import app.yaqeen.domain.model.Sunnah
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PrayerStatsTest {
    private val d1 = LocalDate.of(2026, 11, 1)
    private val d7 = LocalDate.of(2026, 11, 7)

    private fun log(date: LocalDate, prayer: Prayer, status: PrayerStatus, deleted: Boolean = false, updatedAt: Long = 1) =
        PrayerLogEntity("${date}_${prayer.name}", date.toString(), prayer.name, status.name, updatedAt = updatedAt, deleted = deleted)

    @Test fun emptyRangeHasNoRecordsAndNoJamaaPercent() {
        val s = prayerStats(emptyList(), d1, d7)
        assertEquals(7, s.days)
        assertEquals(35, s.possible)
        assertEquals(0, s.recorded)
        assertEquals(35, s.unrecorded)
        assertNull(s.jamaaRatio)
        assertNull(s.jamaaPercent)
        assertEquals(0f, s.coverage, 0f)
        assertFalse(s.hasRecords)
        assertEquals(7, s.perDay.size)
        assertEquals(5, s.perPrayer.size)
        assertTrue(s.perPrayer.all { it.unrecorded == 7 })
    }

    @Test fun unrecordedIsNeverCountedAsNotPrayed() {
        val s = prayerStats(listOf(log(d1, Prayer.FAJR, PrayerStatus.MOSQUE)), d1, d7)
        assertEquals(1, s.recorded)
        assertEquals(0, s.notPrayed)
        assertEquals(34, s.unrecorded)
    }

    @Test fun countsEachStatus() {
        val logs = listOf(
            log(d1, Prayer.FAJR, PrayerStatus.MOSQUE),
            log(d1, Prayer.DHUHR, PrayerStatus.MOSQUE),
            log(d1, Prayer.ASR, PrayerStatus.HOME),
            log(d1, Prayer.MAGHRIB, PrayerStatus.NOT_PRAYED),
            log(d1.plusDays(1), Prayer.FAJR, PrayerStatus.HOME),
        )
        val s = prayerStats(logs, d1, d7)
        assertEquals(5, s.recorded)
        assertEquals(2, s.mosque)
        assertEquals(2, s.home)
        assertEquals(1, s.notPrayed)
        assertEquals(4, s.prayed)
        // جماعة = 2 / (2+2) = 50% ولا يدخل "لم أصلِّ" في المقام
        assertEquals(0.5f, s.jamaaRatio!!, 0.0001f)
        assertEquals(50, s.jamaaPercent)
        assertEquals(5f / 35f, s.coverage, 0.0001f)
        assertEquals(2, s.daysWithRecords)
    }

    @Test fun jamaaZeroDenominatorWhenOnlyNotPrayedRecorded() {
        val s = prayerStats(listOf(log(d1, Prayer.FAJR, PrayerStatus.NOT_PRAYED)), d1, d7)
        assertEquals(1, s.recorded)
        assertEquals(1, s.notPrayed)
        assertNull(s.jamaaRatio)
        assertNull(s.jamaaPercent)
    }

    @Test fun deletedRowsAreExcluded() {
        val logs = listOf(
            log(d1, Prayer.FAJR, PrayerStatus.MOSQUE, deleted = true),
            log(d1, Prayer.DHUHR, PrayerStatus.HOME),
        )
        val s = prayerStats(logs, d1, d7)
        assertEquals(1, s.recorded)
        assertEquals(0, s.mosque)
        assertEquals(1, s.home)
    }

    @Test fun rowsOutsideRangeAreIgnoredAndBoundsInclusive() {
        val logs = listOf(
            log(d1.minusDays(1), Prayer.FAJR, PrayerStatus.MOSQUE),
            log(d1, Prayer.FAJR, PrayerStatus.MOSQUE),
            log(d7, Prayer.ISHA, PrayerStatus.HOME),
            log(d7.plusDays(1), Prayer.FAJR, PrayerStatus.MOSQUE),
        )
        val s = prayerStats(logs, d1, d7)
        assertEquals(2, s.recorded)
        assertEquals(1, s.mosque)
        assertEquals(1, s.home)
    }

    @Test fun invalidRowsAreIgnored() {
        val logs = listOf(
            PrayerLogEntity("a", "not-a-date", "FAJR", "MOSQUE"),
            PrayerLogEntity("b", d1.toString(), "SUNRISE", "MOSQUE"),
            PrayerLogEntity("c", d1.toString(), "FAJR", "UNKNOWN"),
            log(d1, Prayer.ASR, PrayerStatus.HOME),
        )
        val s = prayerStats(logs, d1, d7)
        assertEquals(1, s.recorded)
    }

    @Test fun duplicateRowsTakeLatestUpdatedAt() {
        val logs = listOf(
            log(d1, Prayer.FAJR, PrayerStatus.HOME, updatedAt = 5),
            log(d1, Prayer.FAJR, PrayerStatus.MOSQUE, updatedAt = 9),
            log(d1, Prayer.FAJR, PrayerStatus.NOT_PRAYED, updatedAt = 2),
        )
        val s = prayerStats(logs, d1, d1)
        assertEquals(1, s.recorded)
        assertEquals(1, s.mosque)
    }

    @Test fun asOfClipsFutureDaysFromPossible() {
        val s = prayerStats(emptyList(), d1, d7, asOf = d1.plusDays(2))
        assertEquals(3, s.days)
        assertEquals(15, s.possible)
        assertEquals(d1.plusDays(2), s.to)
        // سجل في يوم مستقبلي يُتجاهل
        val s2 = prayerStats(listOf(log(d7, Prayer.FAJR, PrayerStatus.MOSQUE)), d1, d7, asOf = d1.plusDays(2))
        assertEquals(0, s2.recorded)
    }

    @Test fun asOfBeforeRangeGivesEmptyStats() {
        val s = prayerStats(listOf(log(d1, Prayer.FAJR, PrayerStatus.MOSQUE)), d1, d7, asOf = d1.minusDays(1))
        assertEquals(0, s.days)
        assertEquals(0, s.possible)
        assertEquals(0f, s.coverage, 0f)
        assertTrue(s.perDay.isEmpty())
        assertNull(s.jamaaPercent)
    }

    @Test fun reversedRangeIsEmptyNotCrash() {
        val s = prayerStats(emptyList(), d7, d1)
        assertEquals(0, s.days)
    }

    @Test fun perPrayerBreakdownAndFullyRecordedDays() {
        val logs = Prayer.entries.map { log(d1, it, PrayerStatus.MOSQUE) } + log(d1.plusDays(1), Prayer.FAJR, PrayerStatus.HOME)
        val s = prayerStats(logs, d1, d7)
        assertEquals(1, s.fullyRecordedDays)
        val fajr = s.perPrayer.first { it.prayer == Prayer.FAJR }
        assertEquals(1, fajr.mosque)
        assertEquals(1, fajr.home)
        assertEquals(5, fajr.unrecorded)
        assertEquals(2, fajr.recorded)
        val dhuhr = s.perPrayer.first { it.prayer == Prayer.DHUHR }
        assertEquals(1, dhuhr.prayed)
        assertEquals(6, dhuhr.unrecorded)
    }

    @Test fun perDayIsAscendingAndContainsEveryDay() {
        val s = prayerStats(emptyList(), d1, d7)
        assertEquals(d1, s.perDay.first().date)
        assertEquals(d7, s.perDay.last().date)
        assertEquals(s.perDay.map { it.date }.sorted(), s.perDay.map { it.date })
    }

    // ------------------------------------------------------------ السنن

    private fun rlog(date: LocalDate, id: String, done: Boolean = true, rakaat: Int = 0, deleted: Boolean = false, updatedAt: Long = 1) =
        RawatibLogEntity("${date}_$id", date.toString(), id, done, rakaat, updatedAt = updatedAt, deleted = deleted)

    @Test fun rawatibEmpty() {
        val r = rawatibStats(emptyList(), d1, d7)
        assertEquals(7, r.days)
        assertEquals(0, r.totalRakaat)
        assertEquals(0, r.daysCompleted)
        assertEquals(0, r.daysWithAny)
        assertFalse(r.hasRecords)
        assertTrue(r.perSunnah.isEmpty())
    }

    @Test fun rawatibCompletedDayAndTotals() {
        val all = Sunnah.entries.map { rlog(d1, it.name, rakaat = it.rakaat) }
        val partial = listOf(rlog(d1.plusDays(1), Sunnah.FAJR_BEFORE.name, rakaat = 2))
        val r = rawatibStats(all + partial, d1, d7)
        assertEquals(1, r.daysCompleted)
        assertEquals(2, r.daysWithAny)
        assertEquals(12 + 2, r.totalRakaat) // 2+4+2+2+2 = 12
        assertEquals(6, r.totalDone)
        val fajr = r.perSunnah.first { it.sunnahId == Sunnah.FAJR_BEFORE.name }
        assertEquals(2, fajr.days)
        assertEquals(4, fajr.rakaat)
    }

    @Test fun rawatibIgnoresDeletedAndNotDone() {
        val r = rawatibStats(
            listOf(
                rlog(d1, Sunnah.FAJR_BEFORE.name, rakaat = 2, deleted = true),
                rlog(d1, Sunnah.DHUHR_AFTER.name, done = false, rakaat = 2),
            ),
            d1, d7,
        )
        assertEquals(0, r.totalDone)
        assertEquals(0, r.totalRakaat)
    }

    @Test fun rawatibRakaatFallsBackToDefaultWhenZero() {
        val r = rawatibStats(listOf(rlog(d1, Sunnah.DHUHR_BEFORE.name, rakaat = 0)), d1, d7)
        assertEquals(4, r.totalRakaat)
    }

    @Test fun rawatibCustomSunnahCountsRakaatButNotTowardsCompletedDay() {
        val r = rawatibStats(listOf(rlog(d1, "custom:abc", rakaat = 4)), d1, d7)
        assertEquals(4, r.totalRakaat)
        assertEquals(1, r.daysWithAny)
        assertEquals(0, r.daysCompleted)
        assertEquals("custom:abc", r.perSunnah.single().sunnahId)
    }

    @Test fun rawatibPerSunnahOrderedDefaultsFirst() {
        val r = rawatibStats(
            listOf(rlog(d1, "custom:z", rakaat = 2), rlog(d1, Sunnah.ISHA_AFTER.name, rakaat = 2), rlog(d1, Sunnah.FAJR_BEFORE.name, rakaat = 2)),
            d1, d7,
        )
        assertEquals(listOf(Sunnah.FAJR_BEFORE.name, Sunnah.ISHA_AFTER.name, "custom:z"), r.perSunnah.map { it.sunnahId })
    }

    @Test fun rawatibAsOfAndRangeFilters() {
        val r = rawatibStats(
            listOf(rlog(d1.minusDays(1), Sunnah.FAJR_BEFORE.name, rakaat = 2), rlog(d7, Sunnah.FAJR_BEFORE.name, rakaat = 2)),
            d1, d7, asOf = d1.plusDays(1),
        )
        assertEquals(0, r.totalRakaat)
        assertEquals(2, r.days)
    }

    @Test fun rawatibDuplicateLatestWins() {
        val r = rawatibStats(
            listOf(
                rlog(d1, Sunnah.FAJR_BEFORE.name, done = true, rakaat = 2, updatedAt = 1),
                rlog(d1, Sunnah.FAJR_BEFORE.name, done = false, rakaat = 2, updatedAt = 3),
            ),
            d1, d7,
        )
        assertEquals(0, r.totalDone)
    }
}
