package app.yaqeen.feature.prayer.tracking

import app.yaqeen.domain.model.Prayer
import app.yaqeen.domain.model.PrayerStatus as PS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PrayerFormatTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private fun at(h: Int, m: Int, day: Int = 4) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)

    private val times = mapOf(
        Prayer.FAJR to at(4, 40),
        Prayer.DHUHR to at(11, 35),
        Prayer.ASR to at(14, 50),
        Prayer.MAGHRIB to at(17, 20),
        Prayer.ISHA to at(18, 50),
    )
    private val tomorrowFajr = at(4, 41, day = 5)

    @Test fun timelineBeforeFajr() {
        val t = computeTimeline(times, tomorrowFajr, at(3, 0))
        assertNull(t.current)
        assertEquals(Prayer.FAJR, t.next)
        assertEquals(100L, t.minutesToNext)
    }

    @Test fun timelineBetweenPrayers() {
        val t = computeTimeline(times, tomorrowFajr, at(12, 0))
        assertEquals(Prayer.DHUHR, t.current)
        assertEquals(Prayer.ASR, t.next)
        assertEquals(170L, t.minutesToNext)
    }

    @Test fun timelineExactlyAtPrayerTimeCountsAsCurrent() {
        val t = computeTimeline(times, tomorrowFajr, at(11, 35))
        assertEquals(Prayer.DHUHR, t.current)
        assertEquals(Prayer.ASR, t.next)
    }

    @Test fun timelineAfterIshaPointsToTomorrowFajr() {
        val t = computeTimeline(times, tomorrowFajr, at(21, 0))
        assertEquals(Prayer.ISHA, t.current)
        assertEquals(Prayer.FAJR, t.next)
        assertEquals(tomorrowFajr, t.nextTime)
    }

    @Test fun timelineAfterIshaWithoutTomorrowHasNoNext() {
        val t = computeTimeline(times, null, at(21, 0))
        assertEquals(Prayer.ISHA, t.current)
        assertNull(t.next)
        assertNull(t.minutesToNext)
    }

    @Test fun remainingMinutesRoundUp() {
        val t = computeTimeline(times, tomorrowFajr, ZonedDateTime.of(2026, 11, 4, 11, 34, 30, 0, zone))
        assertEquals(1L, t.minutesToNext) // 30 ثانية -> دقيقة
    }

    @Test fun remainingFormat() {
        assertEquals("أقل من دقيقة", PrayerFormat.remaining(0))
        assertEquals("دقيقة", PrayerFormat.remaining(1))
        assertEquals("دقيقتان", PrayerFormat.remaining(2))
        assertEquals("٥ دقائق", PrayerFormat.remaining(5))
        assertEquals("١٥ دقيقة", PrayerFormat.remaining(15))
        assertEquals("ساعة", PrayerFormat.remaining(60))
        assertEquals("ساعتان", PrayerFormat.remaining(120))
        assertEquals("ساعتان و١٠ دقائق", PrayerFormat.remaining(130))
        assertEquals("٣ ساعات ودقيقة", PrayerFormat.remaining(181))
    }

    @Test fun clock12() {
        assertEquals("٤:٤٠ ص", PrayerFormat.clock12(at(4, 40)))
        assertEquals("١٢:٠٥ م", PrayerFormat.clock12(at(12, 5)))
        assertEquals("١٢:٠٠ ص", PrayerFormat.clock12(at(0, 0)))
        assertEquals("٦:٥٠ م", PrayerFormat.clock12(at(18, 50)))
    }

    @Test fun coverageText() {
        assertEquals("سجّلتَ ٣٢ من ٣٥ صلاة", PrayerFormat.coverage(32, 35))
        assertEquals("٦٠٪", PrayerFormat.percent(60))
    }

    @Test fun rakaatAndDaysText() {
        assertEquals("ركعتان", PrayerFormat.rakaat(2))
        assertEquals("ركعة واحدة", PrayerFormat.rakaat(1))
        assertEquals("٤ ركعات", PrayerFormat.rakaat(4))
        assertEquals("١٢ ركعة", PrayerFormat.rakaat(12))
        assertEquals("يومان", PrayerFormat.days(2))
        assertEquals("١٢ يومًا", PrayerFormat.days(12))
    }

    @Test fun dayLabels() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals("اليوم", PrayerFormat.dayLabel(today, today))
        assertEquals("أمس", PrayerFormat.dayLabel(today.minusDays(1), today))
        assertEquals("الأحد ١ نوفمبر", PrayerFormat.dayLabel(LocalDate.of(2026, 11, 1), today))
        assertEquals("الأربعاء ٣١ ديسمبر ٢٠٢٥", PrayerFormat.dayLabel(LocalDate.of(2025, 12, 31), today))
    }

    @Test fun encouragementNeverBlamesAndCoversAllBranches() {
        val d = LocalDate.of(2026, 11, 1)
        fun stats(vararg pairs: Pair<Prayer, PS>) = prayerStats(
            pairs.map { (p, s) -> app.yaqeen.data.local.PrayerLogEntity("${d}_${p.name}", d.toString(), p.name, s.name) },
            d, d,
        )
        
        val cases = listOf(
            stats(),
            stats(Prayer.FAJR to PS.MOSQUE),
            stats(*Prayer.entries.map { it to PS.MOSQUE }.toTypedArray()),
            stats(Prayer.FAJR to PS.MOSQUE, Prayer.DHUHR to PS.HOME, Prayer.ASR to PS.MOSQUE, Prayer.MAGHRIB to PS.HOME, Prayer.ISHA to PS.HOME),
            stats(*Prayer.entries.map { it to PS.HOME }.toTypedArray()),
            stats(*Prayer.entries.map { it to PS.NOT_PRAYED }.toTypedArray()),
        )
        val forbidden = listOf("مقصّر", "مقصر", "تقصير ", "فاشل", "كسول", "مهمل", "ترتيب", "نقاط")
        for (c in cases) {
            val text = PrayerFormat.encouragement(c)
            assertTrue(text.isNotBlank())
            for (w in forbidden) assertFalse("'$w' in: $text", text.contains(w))
        }
        // أول حالة (بلا سجلات) تقول صراحة إن غير المسجل ليس تقصيرًا
        assertTrue(PrayerFormat.encouragement(cases[0]).contains("لا يُحتسب تقصيرًا"))
    }

    // ------------------------------------------------------------ الفترات

    @Test fun weekStartsOnSaturdayAndEndsFriday() {
        val wed = LocalDate.of(2026, 11, 4) // الأربعاء
        assertEquals(DayOfWeek.WEDNESDAY, wed.dayOfWeek)
        val r = StatsRanges.range(StatsPeriod.WEEK, wed, wed)
        assertEquals(LocalDate.of(2026, 10, 31), r.from)
        assertEquals(DayOfWeek.SATURDAY, r.from.dayOfWeek)
        assertEquals(LocalDate.of(2026, 11, 6), r.to)
        assertEquals(7, r.days)
        // السبت نفسه يبدأ أسبوعه
        assertEquals(r.from, StatsRanges.range(StatsPeriod.WEEK, r.from, wed).from)
    }

    @Test fun monthRange() {
        val r = StatsRanges.range(StatsPeriod.MONTH, LocalDate.of(2026, 2, 14), LocalDate.of(2026, 11, 4))
        assertEquals(LocalDate.of(2026, 2, 1), r.from)
        assertEquals(LocalDate.of(2026, 2, 28), r.to)
        assertEquals(28, r.days)
    }

    @Test fun rollingRangesEndToday() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals(7, StatsRanges.range(StatsPeriod.LAST_7, today, today).days)
        assertEquals(30, StatsRanges.range(StatsPeriod.LAST_30, today, today).days)
        assertEquals(90, StatsRanges.range(StatsPeriod.LAST_90, today, today).days)
        assertEquals(today, StatsRanges.range(StatsPeriod.LAST_30, today, today).to)
        assertEquals(today.minusDays(6), StatsRanges.range(StatsPeriod.LAST_7, today, today).from)
    }

    @Test fun dayRangeAndNavigation() {
        val today = LocalDate.of(2026, 11, 4)
        assertEquals(1, StatsRanges.range(StatsPeriod.DAY, today, today).days)
        assertFalse(StatsRanges.canGoNext(StatsPeriod.DAY, today, today))
        assertTrue(StatsRanges.canGoNext(StatsPeriod.DAY, today.minusDays(1), today))
        assertEquals(today.minusDays(1), StatsRanges.shift(StatsPeriod.DAY, today, -1))
    }

    @Test fun weekAndMonthNavigationStopsAtCurrentPeriod() {
        val today = LocalDate.of(2026, 11, 4)
        assertFalse(StatsRanges.canGoNext(StatsPeriod.WEEK, today, today))
        assertTrue(StatsRanges.canGoNext(StatsPeriod.WEEK, today.minusWeeks(1), today))
        assertFalse(StatsRanges.canGoNext(StatsPeriod.MONTH, today, today))
        assertTrue(StatsRanges.canGoNext(StatsPeriod.MONTH, today.minusMonths(1), today))
        assertFalse(StatsRanges.canGoNext(StatsPeriod.LAST_7, today, today))
        // قبل آخر يوم من الأسبوع الحالي: الأسبوع التالي هو الحالي فيجوز
        assertTrue(StatsRanges.canGoNext(StatsPeriod.WEEK, today.minusDays(8), today))
    }
}
