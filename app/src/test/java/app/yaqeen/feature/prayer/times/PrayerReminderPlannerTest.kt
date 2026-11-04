package app.yaqeen.feature.prayer.times

import app.yaqeen.domain.model.Prayer
import app.yaqeen.feature.prayer.adhan.AdhanVoices
import app.yaqeen.feature.prayer.adhan.VoiceRef
import app.yaqeen.feature.prayer.reminders.PrayerNotifPrefs
import app.yaqeen.feature.prayer.reminders.PrayerReminderKind
import app.yaqeen.feature.prayer.reminders.PrayerReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId

class PrayerReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Riyadh")
    private val days = (0..2).map { PrayerCalculator.compute(LocalDate.of(2026, 11, 4).plusDays(it.toLong()), 17.4917, 44.1277, zone, CalcSettings())!! }
    private val allOn = Prayer.entries.associateWith { PrayerNotifPrefs(adhan = true, preMinutes = 10, iqamaMinutes = 15, voice = "default") }
    private val early = days.first().fajr.minusHours(3).toInstant()

    @Test fun keys_are_stable_and_formatted() {
        assertEquals("prayer:2026-11-04:FAJR:adhan", PrayerReminderPlanner.key(LocalDate.of(2026, 11, 4), Prayer.FAJR, PrayerReminderKind.ADHAN))
        val plan = PrayerReminderPlanner.plan(days, allOn, early)
        assertEquals(3 * 5 * 3, plan.size)
        assertEquals(plan.size, plan.map { it.key }.toSet().size)
        assertTrue(plan.all { it.key.startsWith("prayer:") })
        assertEquals(plan, PrayerReminderPlanner.plan(days, allOn, early)) // حتمي: لا تكرار عند إعادة الجدولة
    }

    @Test fun offsets_are_correct() {
        val plan = PrayerReminderPlanner.plan(days, allOn, early)
        val d = days.first()
        val pre = plan.first { it.key == "prayer:2026-11-04:ASR:pre" }
        val adhan = plan.first { it.key == "prayer:2026-11-04:ASR:adhan" }
        val iqama = plan.first { it.key == "prayer:2026-11-04:ASR:iqama" }
        assertEquals(d.asr, adhan.at)
        assertEquals(10L, Duration.between(pre.at, adhan.at).toMinutes())
        assertEquals(15L, Duration.between(adhan.at, iqama.at).toMinutes())
    }

    @Test fun zero_minutes_and_disabled_adhan_are_off() {
        val prefs = Prayer.entries.associateWith { PrayerNotifPrefs(adhan = false, preMinutes = 0, iqamaMinutes = 0) }
        assertTrue(PrayerReminderPlanner.plan(days, prefs, early).isEmpty())
        val onlyPre = Prayer.entries.associateWith { PrayerNotifPrefs(adhan = false, preMinutes = 5, iqamaMinutes = 0) }
        assertTrue(PrayerReminderPlanner.plan(days, onlyPre, early).all { it.kind == PrayerReminderKind.PRE })
    }

    @Test fun past_reminders_are_dropped_and_sorted() {
        val now = days.first().dhuhr.toInstant()
        val plan = PrayerReminderPlanner.plan(days, allOn, now)
        assertTrue(plan.all { it.at.toInstant().isAfter(now) })
        assertTrue(plan.map { it.at.toInstant() }.zipWithNext().all { (a, b) -> !a.isAfter(b) })
        assertTrue(plan.none { it.key == "prayer:2026-11-04:FAJR:adhan" })
    }

    @Test fun arabic_texts() {
        val plan = PrayerReminderPlanner.plan(days, allOn, early)
        val pre = plan.first { it.kind == PrayerReminderKind.PRE && it.prayer == Prayer.MAGHRIB }
        val iq = plan.first { it.kind == PrayerReminderKind.IQAMA && it.prayer == Prayer.DHUHR }
        val ad = plan.first { it.kind == PrayerReminderKind.ADHAN && it.prayer == Prayer.FAJR }
        assertEquals("صلاة المغرب بعد ١٠ دقائق", PrayerReminderPlanner.title(pre))
        assertEquals("موعد إقامة صلاة الظهر", PrayerReminderPlanner.title(iq))
        assertEquals("حان وقت صلاة الفجر", PrayerReminderPlanner.title(ad))
        assertEquals("مضت ١٥ دقيقة على أذان الظهر – نجران", PrayerReminderPlanner.text(iq, "١١:٤٧ ص", "نجران"))
        assertEquals("نجران – ٤:٥٠ ص", PrayerReminderPlanner.text(ad, "٤:٥٠ ص", "نجران"))
    }

    @Test fun voice_refs_parse_and_urls_validate() {
        assertEquals(VoiceRef.Default, AdhanVoices.parse(null))
        assertEquals(VoiceRef.Default, AdhanVoices.parse("default"))
        assertEquals(VoiceRef.File("a.mp3"), AdhanVoices.parse("file:a.mp3"))
        assertEquals(VoiceRef.Raw("adhan1"), AdhanVoices.parse("raw:adhan1"))
        assertEquals(VoiceRef.Default, AdhanVoices.parse("file:"))
        assertEquals(VoiceRef.Default, AdhanVoices.parse("weird"))
        assertEquals("adhan", AdhanVoices.sanitizeFileName("../../.."))
        assertEquals("evil.mp3", AdhanVoices.sanitizeFileName("../x/evil.mp3"))
        assertEquals("a_b.mp3", AdhanVoices.sanitizeFileName("a:b.mp3"))
        assertEquals("الأذان", AdhanVoices.titleOfFile("الأذان.mp3"))
        assertNull(AdhanVoices.validateUrl("https://example.org/adhan.mp3"))
        assertTrue(AdhanVoices.validateUrl("http://example.org/adhan.mp3") != null)
        assertTrue(AdhanVoices.validateUrl("https://www.youtube.com/watch?v=abc") != null)
        assertTrue(AdhanVoices.validateUrl("https://youtu.be/abc") != null)
        assertTrue(AdhanVoices.validateUrl("not a url") != null)
        assertEquals("mp3", AdhanVoices.extensionForContentType("audio/mpeg; charset=x"))
    }
}
