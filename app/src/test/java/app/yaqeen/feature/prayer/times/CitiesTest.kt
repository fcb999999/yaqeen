package app.yaqeen.feature.prayer.times

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class CitiesTest {
    private val cities: List<City> by lazy {
        CityRepository.parse(javaClass.classLoader!!.getResourceAsStream("cities/cities.json")!!.bufferedReader(Charsets.UTF_8).use { it.readText() })
    }

    @Test fun list_is_large_valid_and_unique() {
        assertTrue("count=${cities.size}", cities.size >= 150)
        cities.forEach { c ->
            assertTrue(c.name, c.lat in -90.0..90.0 && c.lon in -180.0..180.0)
            assertTrue(c.name, c.name.isNotBlank() && c.country.isNotBlank())
            ZoneId.of(c.zone) // يرمي استثناء إن كان المعرّف خاطئًا
        }
        assertEquals(cities.size, cities.map { it.name }.toSet().size)
    }

    @Test fun required_saudi_cities_exist_with_accurate_coordinates() {
        val sa = cities.filter { it.country == "المملكة العربية السعودية" }
        assertTrue(sa.size >= 40)
        listOf("نجران", "الرياض", "جدة", "مكة المكرمة", "المدينة المنورة", "الدمام", "أبها", "جازان", "تبوك", "حائل", "بريدة", "الطائف", "ينبع", "الخبر")
            .forEach { n -> assertNotNull(n, sa.firstOrNull { it.name == n }) }
        assertTrue(sa.all { it.zone == "Asia/Riyadh" })
        fun near(n: String, lat: Double, lon: Double) {
            val c = sa.first { it.name == n }
            assertTrue("$n ${c.lat},${c.lon}", Math.abs(c.lat - lat) < 0.05 && Math.abs(c.lon - lon) < 0.05)
        }
        near("نجران", 17.49, 44.13); near("الرياض", 24.71, 46.68); near("مكة المكرمة", 21.39, 39.86)
        near("المدينة المنورة", 24.47, 39.61); near("أبها", 18.22, 42.51); near("جازان", 16.89, 42.55)
    }

    @Test fun search_is_arabic_normalized_and_multilingual() {
        assertEquals("جدة", CityRepository.search(cities, "جده").first().name)
        assertEquals("جدة", CityRepository.search(cities, "jeddah").first().name)
        assertEquals("أبها", CityRepository.search(cities, "ابها").first().name)
        assertEquals("المدينة المنورة", CityRepository.search(cities, "المدينه").first().name)
        assertTrue(CityRepository.search(cities, "مصر").all { it.country == "مصر" })
        assertEquals(cities.size, CityRepository.search(cities, "  ").size)
        assertTrue(CityRepository.search(cities, "xyzxyz").isEmpty())
    }

    @Test fun nearest_city() {
        assertEquals("الرياض", CityRepository.nearest(cities, 24.75, 46.70)?.name)
        assertNull(CityRepository.nearest(cities, 0.0, -30.0)) // وسط الأطلسي
    }

    @Test fun suggested_method_by_country() {
        assertEquals("UMM_AL_QURA", CalcMethods.suggestedForCountry("المملكة العربية السعودية"))
        assertEquals("EGYPTIAN", CalcMethods.suggestedForCountry("مصر"))
        assertNull(CalcMethods.suggestedForCountry("فرنسا"))
    }

    @Test fun service_builds_location_from_raw_prefs() {
        val manual = PrayerTimesService.buildLocation("24.7", "46.6", "الرياض", "manual", "Asia/Riyadh")!!
        assertTrue(manual.isManual); assertEquals("Asia/Riyadh", manual.zoneId); assertEquals("الرياض", manual.name)
        assertNull(PrayerTimesService.buildLocation(null, "46.6", "x", "manual", "Asia/Riyadh"))
        assertNull(PrayerTimesService.buildLocation("abc", "46.6", "x", "manual", "Asia/Riyadh"))
        assertNull(PrayerTimesService.buildLocation("124", "46.6", "x", "manual", "Asia/Riyadh"))
        val badZone = PrayerTimesService.buildLocation("24.7", "46.6", null, "manual", "Not/AZone")!!
        assertEquals(ZoneId.systemDefault().id, badZone.zoneId)
        assertEquals(PrayerTimesService.CURRENT_LOCATION_NAME, badZone.name)
        val gps = PrayerTimesService.buildLocation("24.7", "46.6", "الرياض", "gps", "Asia/Tokyo")!!
        assertEquals(false, gps.isManual); assertEquals(ZoneId.systemDefault().id, gps.zoneId)
    }
}
