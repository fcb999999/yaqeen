package app.yaqeen.feature.calendar

import app.yaqeen.core.HijriCalendar
import app.yaqeen.core.HijriDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * المرجع: جدول أم القرى (JDK HijrahChronology). المعروف: 1 رمضان 1447 = 2026-02-18،
 * عيد الفطر 1447 = 2026-03-20، عرفة 1447 = 2026-05-26، عيد الأضحى 1447 = 2026-05-27.
 * الرؤية الفعلية قد تختلف بيومٍ عن الجدول الحسابي، لذلك نتحمّل فرق يوم واحد في المقارنة بالتواريخ المنشورة.
 */
class IslamicEventsTest {
    private fun near(expected: LocalDate, actual: LocalDate) =
        assertTrue("expected≈$expected actual=$actual", abs(ChronoUnit.DAYS.between(expected, actual)) <= 1)

    @Test fun ramadan1447() {
        val e = IslamicEvents.monthEvents(1447, 9, 0).first { it.key == IslamicEventKeys.RAMADAN_START }
        near(LocalDate.of(2026, 2, 18), e.date)
        assertEquals(HijriDate(1447, 9, 1), e.hijri)
        assertEquals(e.date, HijriCalendar.toGregorian(HijriDate(1447, 9, 1)))
    }

    @Test fun eidFitrArafahAdha1447() {
        near(LocalDate.of(2026, 3, 20), IslamicEvents.monthEvents(1447, 10, 0).first { it.key == IslamicEventKeys.EID_FITR }.date)
        val dh = IslamicEvents.monthEvents(1447, 12, 0)
        near(LocalDate.of(2026, 5, 26), dh.first { it.key == IslamicEventKeys.ARAFAH }.date)
        near(LocalDate.of(2026, 5, 27), dh.first { it.key == IslamicEventKeys.EID_ADHA }.date)
        // التروية قبل عرفة بيوم، وعرفة قبل العيد بيوم
        val tar = dh.first { it.key == IslamicEventKeys.TARWIYAH }.date
        val ara = dh.first { it.key == IslamicEventKeys.ARAFAH }.date
        val adha = dh.first { it.key == IslamicEventKeys.EID_ADHA }.date
        assertEquals(ara.minusDays(1), tar)
        assertEquals(ara.plusDays(1), adha)
        // التشريق 11..13
        val tash = dh.first { it.key == IslamicEventKeys.TASHREEQ }
        assertEquals(adha.plusDays(1), tash.date)
        assertEquals(adha.plusDays(3), tash.endDate)
    }

    @Test fun ashuraAndTasuaAndNewYear() {
        val m = IslamicEvents.monthEvents(1448, 1, 0)
        val ny = m.first { it.key == IslamicEventKeys.HIJRI_NEW_YEAR }
        val tasua = m.first { it.key == IslamicEventKeys.TASUA }
        val ashura = m.first { it.key == IslamicEventKeys.ASHURA }
        assertEquals(HijriDate(1448, 1, 1), ny.hijri)
        assertEquals(ny.date.plusDays(8), tasua.date)
        assertEquals(ny.date.plusDays(9), ashura.date)
        near(LocalDate.of(2026, 6, 25), ashura.date)
    }

    @Test fun whiteDaysEveryMonthAre13To15() {
        for (month in 1..12) {
            val w = IslamicEvents.monthEvents(1447, month, 0).first { it.key == IslamicEventKeys.WHITE_DAYS }
            assertEquals(13, w.hijri.day)
            assertEquals(w.date.plusDays(2), w.endDate)
            assertEquals(HijriDate(1447, month, 15), HijriCalendar.fromGregorian(w.endDate!!))
        }
    }

    @Test fun sacredMonthsAreRangesOverWholeMonth() {
        for (month in listOf(1, 7, 11, 12)) {
            val s = IslamicEvents.monthEvents(1447, month, 0).first { it.key == IslamicEventKeys.SACRED_MONTH }
            assertEquals(1, s.hijri.day)
            assertEquals(HijriCalendar.lengthOfMonth(1447, month) - 1L, ChronoUnit.DAYS.between(s.date, s.endDate))
            assertFalse(s.notifiable)
        }
        for (month in listOf(2, 3, 4, 5, 6, 8, 9, 10)) {
            assertTrue(IslamicEvents.monthEvents(1447, month, 0).none { it.key == IslamicEventKeys.SACRED_MONTH })
        }
    }

    @Test fun monthStartsOnlyWhereNoSpecificEvent() {
        for (month in 1..12) {
            val starts = IslamicEvents.monthEvents(1447, month, 0).filter { it.key == IslamicEventKeys.MONTH_START }
            if (month in listOf(1, 9, 10)) assertTrue(starts.isEmpty()) else assertEquals(1, starts.size)
        }
        val dh = IslamicEvents.monthEvents(1447, 12, 0).first { it.key == IslamicEventKeys.MONTH_START }
        assertEquals("بداية شهر ذي الحجة", dh.nameAr)
    }

    @Test fun lastTenAndLaylatAlQadr() {
        val m = IslamicEvents.monthEvents(1447, 9, 0)
        val last = m.first { it.key == IslamicEventKeys.LAST_TEN_RAMADAN }
        val q = m.first { it.key == IslamicEventKeys.LAYLAT_AL_QADR }
        assertEquals(21, last.hijri.day)
        assertEquals(HijriCalendar.lengthOfMonth(1447, 9) - 21L, ChronoUnit.DAYS.between(last.date, last.endDate))
        assertEquals(27, q.hijri.day)
        assertTrue(q.note.contains("٢٧"))
        assertTrue(q.note.contains("الوتر"))
    }

    @Test fun noMawlidOrIsraa() {
        val names = (1..12).flatMap { IslamicEvents.monthEvents(1447, it, 0) }.map { it.nameAr }
        assertTrue(names.none { it.contains("المولد") || it.contains("الإسراء") || it.contains("المعراج") })
    }

    // ---------------- الواجهة العامة
    @Test fun upcomingIsSortedLimitedAndCountsDays() {
        val from = LocalDate.of(2026, 11, 4)
        val u = IslamicEvents.upcoming(from, 0, 6)
        assertEquals(6, u.size)
        assertEquals(u.sortedBy { it.date }.map { it.date }, u.map { it.date })
        assertTrue(u.all { !it.date.isBefore(from) })
        for (e in u) assertEquals(ChronoUnit.DAYS.between(from, e.date).toInt(), e.daysUntil)
        assertEquals(0, IslamicEvents.upcoming(from, 0, 0).size)
        // يشمل اليوم نفسه: 1 رمضان 1447 مثلًا
        val ram = IslamicEvents.monthEvents(1447, 9, 0).first { it.key == IslamicEventKeys.RAMADAN_START }.date
        val today = IslamicEvents.upcoming(ram, 0, 3)
        assertEquals(0, today.first { it.key == IslamicEventKeys.RAMADAN_START }.daysUntil)
    }

    @Test fun upcomingEventuallyReachesAllMajorEvents() {
        val u = IslamicEvents.upcoming(LocalDate.of(2026, 11, 4), 0, 200)
        val keys = u.map { it.key }.toSet()
        for (k in listOf(
            IslamicEventKeys.RAMADAN_START, IslamicEventKeys.EID_FITR, IslamicEventKeys.ARAFAH, IslamicEventKeys.EID_ADHA,
            IslamicEventKeys.ASHURA, IslamicEventKeys.HIJRI_NEW_YEAR, IslamicEventKeys.TARWIYAH, IslamicEventKeys.WHITE_DAYS,
        )) assertTrue("missing $k", k in keys)
        // أقرب رمضان بعد 2026-11-04 هو 1448 (فبراير 2027)
        val ram = u.first { it.key == IslamicEventKeys.RAMADAN_START }
        assertEquals(1448, ram.hijri.year)
        assertEquals(LocalDate.of(2027, 2, 8), ram.date)
    }

    @Test fun onReturnsRangeEventsForEveryCoveredDay() {
        val tash = IslamicEvents.monthEvents(1447, 12, 0).first { it.key == IslamicEventKeys.TASHREEQ }
        for (i in 0..2) {
            val on = IslamicEvents.on(tash.date.plusDays(i.toLong()))
            assertTrue(on.any { it.key == IslamicEventKeys.TASHREEQ })
            assertTrue(on.all { it.daysUntil == 0 })
        }
        assertFalse(IslamicEvents.on(tash.date.plusDays(3)).any { it.key == IslamicEventKeys.TASHREEQ })
        // يوم 12 ذو الحجة يقع في الأشهر الحرم أيضًا
        assertTrue(IslamicEvents.on(tash.date).any { it.key == IslamicEventKeys.SACRED_MONTH })
    }

    @Test fun onOrdersMajorEventsFirst() {
        val m = IslamicEvents.monthEvents(1448, 1, 0)
        val ny = m.first { it.key == IslamicEventKeys.HIJRI_NEW_YEAR }.date
        val on = IslamicEvents.on(ny)
        assertEquals(IslamicEventKeys.HIJRI_NEW_YEAR, on.first().key)
        assertNotNull(on.firstOrNull { it.key == IslamicEventKeys.SACRED_MONTH })
    }

    @Test fun adjustShiftsGregorianDates() {
        // adjust=+1: الهجري يتقدّم يومًا، فيقع الحدث نفسه قبل يوم ميلادي
        val base = IslamicEvents.monthEvents(1447, 9, 0).first { it.key == IslamicEventKeys.RAMADAN_START }.date
        val plus = IslamicEvents.monthEvents(1447, 9, 1).first { it.key == IslamicEventKeys.RAMADAN_START }.date
        val minus = IslamicEvents.monthEvents(1447, 9, -2).first { it.key == IslamicEventKeys.RAMADAN_START }.date
        assertEquals(base.minusDays(1), plus)
        assertEquals(base.plusDays(2), minus)
        // on() متسق مع التعديل
        assertTrue(IslamicEvents.on(plus, 1).any { it.key == IslamicEventKeys.RAMADAN_START })
        assertFalse(IslamicEvents.on(base, 1).any { it.key == IslamicEventKeys.RAMADAN_START })
        // upcoming متسق
        assertEquals(plus, IslamicEvents.upcoming(plus.minusDays(5), 1, 50).first { it.key == IslamicEventKeys.RAMADAN_START }.date)
    }

    @Test fun startingBetweenIsInclusiveAndConsistentWithUpcoming() {
        val from = LocalDate.of(2026, 11, 4)
        val to = from.plusDays(60)
        val between = IslamicEvents.startingBetween(from, to, 0)
        assertTrue(between.all { !it.date.isBefore(from) && !it.date.isAfter(to) })
        val up = IslamicEvents.upcoming(from, 0, 500).filter { !it.date.isAfter(to) }
        assertEquals(up.map { it.key to it.date }, between.map { it.key to it.date })
        assertTrue(IslamicEvents.startingBetween(to, from, 0).isEmpty())
    }

    @Test fun everyEventHijriDateRoundTripsToItsGregorianDate() {
        val all = IslamicEvents.upcoming(LocalDate.of(2026, 1, 1), 0, 300)
        for (e in all) assertEquals(e.hijri, HijriCalendar.fromGregorian(e.date, 0))
    }
}
