package app.yaqeen.feature.prayer.times

import app.yaqeen.domain.model.Prayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class PrayerCalculatorTest {
    private val riyadh = ZoneId.of("Asia/Riyadh")
    private val uq = CalcSettings()
    private val date = LocalDate.of(2026, 11, 4)

    private fun day(lat: Double, lon: Double, zone: ZoneId, d: LocalDate = date, s: CalcSettings = uq) =
        PrayerCalculator.compute(d, lat, lon, zone, s)!!

    private fun assertOrdered(t: PrayerTimesDay) {
        assertTrue("fajr<sunrise", t.fajr.isBefore(t.sunrise))
        assertTrue("sunrise<dhuhr", t.sunrise.isBefore(t.dhuhr))
        assertTrue("dhuhr<asr", t.dhuhr.isBefore(t.asr))
        assertTrue("asr<maghrib", t.asr.isBefore(t.maghrib))
        assertTrue("maghrib<isha", t.maghrib.isBefore(t.isha))
    }

    @Test fun ordering_for_saudi_cities() {
        listOf(
            Pair(21.3891, 39.8579), Pair(17.4917, 44.1277), Pair(24.7136, 46.6753),
            Pair(18.2164, 42.5053), Pair(28.3835, 36.5662),
        ).forEach { (lat, lon) -> assertOrdered(day(lat, lon, riyadh)) }
    }

    @Test fun makkah_times_are_in_plausible_local_windows() {
        val t = day(21.3891, 39.8579, riyadh)
        // تقويم أم القرى لمكة في 4 نوفمبر تقريبًا: الفجر 5:02، الشروق 6:19، الظهر 11:56، العصر 15:12، المغرب 17:33 (±10 دقائق)
        fun near(actual: ZonedDateTime, h: Int, m: Int) {
            val diff = Math.abs(Duration.between(actual.toLocalTime(), java.time.LocalTime.of(h, m)).toMinutes())
            assertTrue("${actual.toLocalTime()} vs $h:$m", diff <= 10)
        }
        near(t.fajr, 5, 2); near(t.sunrise, 6, 19); near(t.dhuhr, 11, 56); near(t.asr, 15, 12); near(t.maghrib, 17, 33)
        assertEquals(0, t.fajr.second); assertEquals(0, t.fajr.nano)
        assertEquals(riyadh, t.fajr.zone)
    }

    @Test fun umm_al_qura_isha_is_maghrib_plus_90_outside_ramadan() {
        val t = day(21.3891, 39.8579, riyadh)
        assertEquals(90L, Duration.between(t.maghrib, t.isha).toMinutes())
        val najran = day(17.4917, 44.1277, riyadh)
        assertEquals(90L, Duration.between(najran.maghrib, najran.isha).toMinutes())
    }

    @Test fun umm_al_qura_isha_is_maghrib_plus_120_in_ramadan() {
        // رمضان 1447هـ يوافق تقريبًا 19 فبراير - 19 مارس 2026
        val t = day(21.3891, 39.8579, riyadh, LocalDate.of(2026, 3, 1))
        assertEquals(120L, Duration.between(t.maghrib, t.isha).toMinutes())
        val outside = day(21.3891, 39.8579, riyadh, LocalDate.of(2026, 5, 1))
        assertEquals(90L, Duration.between(outside.maghrib, outside.isha).toMinutes())
    }

    @Test fun other_methods_do_not_get_ramadan_extension() {
        val t = day(21.3891, 39.8579, riyadh, LocalDate.of(2026, 3, 1), CalcSettings(method = "MUSLIM_WORLD_LEAGUE"))
        assertTrue(Duration.between(t.maghrib, t.isha).toMinutes() < 110)
    }

    @Test fun timezone_conversion_moves_clock_not_instant() {
        val inRiyadh = day(24.7136, 46.6753, riyadh)
        val utc = PrayerCalculator.compute(date, 24.7136, 46.6753, ZoneId.of("UTC"), uq)!!
        assertEquals(inRiyadh.dhuhr.toInstant(), utc.dhuhr.toInstant())
        assertEquals(3L, (inRiyadh.dhuhr.hour - utc.dhuhr.hour).toLong())
    }

    @Test fun diaspora_zone_dst_is_respected() {
        val ny = ZoneId.of("America/New_York")
        val summer = day(40.7128, -74.0060, ny, LocalDate.of(2026, 7, 1), CalcSettings(method = "NORTH_AMERICA"))
        val winter = day(40.7128, -74.0060, ny, LocalDate.of(2026, 1, 1), CalcSettings(method = "NORTH_AMERICA"))
        assertOrdered(summer); assertOrdered(winter)
        assertEquals(-4, summer.dhuhr.offset.totalSeconds / 3600)
        assertEquals(-5, winter.dhuhr.offset.totalSeconds / 3600)
    }

    @Test fun hanafi_asr_is_later_than_shafi() {
        val s = day(24.7136, 46.6753, riyadh)
        val h = day(24.7136, 46.6753, riyadh, s = CalcSettings(madhab = "HANAFI"))
        assertTrue(h.asr.isAfter(s.asr))
        assertEquals(s.dhuhr, h.dhuhr)
    }

    @Test fun adjustments_shift_exactly_minutes() {
        val base = day(24.7136, 46.6753, riyadh)
        val adj = day(24.7136, 46.6753, riyadh, s = CalcSettings(adjustments = MinuteAdjustments(fajr = -2, dhuhr = 3, asr = 0, maghrib = 1, isha = 5)))
        assertEquals(-2L, Duration.between(base.fajr, adj.fajr).toMinutes())
        assertEquals(3L, Duration.between(base.dhuhr, adj.dhuhr).toMinutes())
        assertEquals(0L, Duration.between(base.asr, adj.asr).toMinutes())
        assertEquals(1L, Duration.between(base.maghrib, adj.maghrib).toMinutes())
        assertEquals(5L, Duration.between(base.isha, adj.isha).toMinutes())
        assertEquals(base.sunrise, adj.sunrise)
    }

    @Test fun adjustments_encode_decode_roundtrip_and_clamp() {
        val a = MinuteAdjustments(-2, 3, 0, 1, 5)
        assertEquals(a, MinuteAdjustments.decode(a.encode()))
        assertEquals(MinuteAdjustments(), MinuteAdjustments.decode(null))
        assertEquals(MinuteAdjustments(), MinuteAdjustments.decode("garbage"))
        assertEquals(MinuteAdjustments(fajr = 30, dhuhr = -30), MinuteAdjustments.decode("99,-99,0,0,0"))
        assertEquals(30, MinuteAdjustments().with(Prayer.ASR, 500)[Prayer.ASR])
    }

    @Test fun all_methods_compute_and_unknown_falls_back() {
        CalcMethods.ALL.forEach { m -> assertOrdered(day(24.7136, 46.6753, riyadh, s = CalcSettings(method = m.id))) }
        val fallback = day(24.7136, 46.6753, riyadh, s = CalcSettings(method = "NOPE"))
        assertEquals(day(24.7136, 46.6753, riyadh).fajr, fallback.fajr)
    }

    @Test fun methods_differ_by_angle() {
        val uqFajr = day(24.7136, 46.6753, riyadh).fajr
        val isna = day(24.7136, 46.6753, riyadh, s = CalcSettings(method = "NORTH_AMERICA")).fajr
        assertTrue(isna.isAfter(uqFajr)) // زاوية 15 درجة أصغر من 18.5 فالفجر أبكر في أم القرى
    }

    @Test fun high_latitude_rules_still_compute_in_london_summer() {
        val london = ZoneId.of("Europe/London")
        for (rule in listOf("AUTO", "MIDDLE_OF_THE_NIGHT", "SEVENTH_OF_THE_NIGHT", "TWILIGHT_ANGLE")) {
            val t = day(51.5074, -0.1278, london, LocalDate.of(2026, 6, 21), CalcSettings(method = "MUSLIM_WORLD_LEAGUE", highLatRule = rule))
            assertNotNull(rule, t)
            assertTrue(rule, t.fajr.isBefore(t.sunrise))
        }
    }

    @Test fun invalid_coordinates_return_null() {
        assertNull(PrayerCalculator.compute(date, 95.0, 0.0, riyadh, uq))
        assertNull(PrayerCalculator.compute(date, 0.0, 200.0, riyadh, uq))
    }

    @Test fun next_prayer_logic() {
        val today = day(24.7136, 46.6753, riyadh)
        val tomorrow = day(24.7136, 46.6753, riyadh, date.plusDays(1))
        val beforeFajr = today.fajr.minusMinutes(5)
        assertEquals(Prayer.FAJR, PrayerCalculator.nextAfter(beforeFajr, today, tomorrow).prayer)
        val afterSunrise = today.sunrise.plusMinutes(1)
        assertEquals(Prayer.DHUHR, PrayerCalculator.nextAfter(afterSunrise, today, tomorrow).prayer)
        val afterIsha = today.isha.plusMinutes(1)
        val n = PrayerCalculator.nextAfter(afterIsha, today, tomorrow)
        assertEquals(Prayer.FAJR, n.prayer)
        assertEquals(tomorrow.fajr, n.time)
        // لحظة الأذان نفسها: الصلاة القادمة هي التي بعدها
        assertEquals(Prayer.ASR, PrayerCalculator.nextAfter(today.dhuhr, today, tomorrow).prayer)
        assertEquals(Prayer.ISHA, PrayerCalculator.currentAt(today.isha, today))
        assertNull(PrayerCalculator.currentAt(beforeFajr, today))
    }

    @Test fun dst_day_in_new_york_has_consistent_instants() {
        // يوم تغيير التوقيت الصيفي 8 مارس 2026
        val ny = ZoneId.of("America/New_York")
        val t = day(40.7128, -74.0060, ny, LocalDate.of(2026, 3, 8), CalcSettings(method = "NORTH_AMERICA"))
        assertOrdered(t)
        assertEquals(ZonedDateTime.of(2026, 3, 8, 0, 0, 0, 0, ny).toLocalDate(), t.dhuhr.toLocalDate())
    }
}
