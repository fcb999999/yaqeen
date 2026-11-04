package app.yaqeen.feature.prayer.times

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class PrayerFormatTest {
    private fun t(h: Int, m: Int) = ZonedDateTime.of(2026, 11, 4, h, m, 0, 0, ZoneId.of("Asia/Riyadh"))

    @Test fun clock_12h_and_24h() {
        assertEquals("٤:٥٢ ص", PrayerFormat.clock(t(4, 52)))
        assertEquals("١٢:٠٥ م", PrayerFormat.clock(t(12, 5)))
        assertEquals("١٢:٣٠ ص", PrayerFormat.clock(t(0, 30)))
        assertEquals("٥:٣٥ م", PrayerFormat.clock(t(17, 35)))
        assertEquals("١٧:٣٥", PrayerFormat.clock(t(17, 35), is24h = true))
        assertEquals("٠٤:٥٢", PrayerFormat.clock(t(4, 52), is24h = true))
    }

    @Test fun countdown() {
        assertEquals("٠٢:١٤:٣٣", PrayerFormat.countdown(2 * 3600 + 14 * 60 + 33L))
        assertEquals("٠٠:٠٠:٠٠", PrayerFormat.countdown(-5))
    }

    @Test fun arabic_minutes_plurals() {
        assertEquals("دقيقة", PrayerFormat.minutes(1))
        assertEquals("دقيقتان", PrayerFormat.minutes(2))
        assertEquals("٥ دقائق", PrayerFormat.minutes(5))
        assertEquals("١٠ دقائق", PrayerFormat.minutes(10))
        assertEquals("١١ دقيقة", PrayerFormat.minutes(11))
        assertEquals("٤٥ دقيقة", PrayerFormat.minutes(45))
    }

    @Test fun offsets_degrees_distance() {
        assertEquals("بدون تعديل", PrayerFormat.offset(0))
        assertEquals("+٢ د", PrayerFormat.offset(2))
        assertEquals("−٣ د", PrayerFormat.offset(-3))
        assertEquals("٢٤٤٫٨°", PrayerFormat.degrees(244.8))
        assertEquals("١٬٢٣٤ كم", PrayerFormat.km(1234.4))
    }
}
