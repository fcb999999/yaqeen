package app.yaqeen.feature.prayer.times

import com.batoulapps.adhan.Coordinates
import com.batoulapps.adhan.Qibla
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaMathTest {
    @Test fun matches_adhan_library_for_all_cities() {
        val cities = CityRepository.parse(resource("cities/cities.json"))
        cities.forEach { c ->
            val mine = QiblaMath.bearingDegrees(c.lat, c.lon)
            val lib = Qibla(Coordinates(c.lat, c.lon)).direction
            assertTrue("${c.en}: mine=$mine lib=$lib", QiblaMath.absDelta(mine, lib) < 0.5)
        }
    }

    @Test fun known_reference_bearings() {
        // الرياض: القبلة نحو الغرب-الجنوب الغربي (243.8 درجة من الشمال؛ حُسبت يدويًا بصيغة الدائرة الكبرى)
        assertEquals(243.8, QiblaMath.bearingDegrees(24.7136, 46.6753), 0.2)
        assertEquals(136.2, QiblaMath.bearingDegrees(30.0444, 31.2357), 0.8)
        assertEquals(118.9, QiblaMath.bearingDegrees(51.5074, -0.1278), 0.8)
        assertEquals(58.5, QiblaMath.bearingDegrees(40.7128, -74.0060), 0.8)
        assertEquals(295.1, QiblaMath.bearingDegrees(-6.2088, 106.8456), 0.8)
        // نجران: القبلة شمال-غرب
        val najran = QiblaMath.bearingDegrees(17.4917, 44.1277)
        assertTrue("najran=$najran", najran in 270.0..360.0)
        val atKaaba = QiblaMath.bearingDegrees(QiblaMath.KAABA_LAT, QiblaMath.KAABA_LON)
        assertTrue(atKaaba in 0.0..360.0)
    }

    @Test fun distance_riyadh_to_kaaba_about_790km() {
        assertEquals(790.0, QiblaMath.distanceKm(24.7136, 46.6753), 25.0)
    }

    @Test fun angle_helpers() {
        assertEquals(10.0, QiblaMath.signedDelta(350.0, 0.0), 1e-9)
        assertEquals(-10.0, QiblaMath.signedDelta(0.0, 350.0), 1e-9)
        assertEquals(180.0, QiblaMath.absDelta(0.0, 180.0), 1e-9)
        assertEquals(350.0, QiblaMath.normalize(-10.0), 1e-9)
        assertEquals(0.0, QiblaMath.normalize(360.0), 1e-9)
        assertEquals("جنوب غرب", QiblaMath.directionName(243.8))
        assertEquals("شمال", QiblaMath.directionName(359.0))
    }

    @Test fun low_pass_converges_and_handles_wraparound() {
        val f = CircularLowPass(0.2)
        assertEquals(350.0, f.update(350.0), 1e-9)
        var v = 0.0
        repeat(60) { v = f.update(10.0) } // يعبر 360 إلى 0 بالمسار الأقصر
        assertTrue("v=$v", QiblaMath.absDelta(v, 10.0) < 0.5)
        val g = CircularLowPass(0.5)
        g.update(350.0)
        val step = g.update(10.0)
        assertEquals(0.0, QiblaMath.signedDelta(step, 0.0), 1e-9)
        val h = CircularLowPass(0.1)
        var last = 0.0
        repeat(200) { i -> last = h.update(90.0 + if (i % 2 == 0) 6.0 else -6.0) }
        assertEquals(90.0, last, 2.0)
    }

    private fun resource(path: String): String =
        javaClass.classLoader!!.getResourceAsStream(path)!!.bufferedReader(Charsets.UTF_8).use { it.readText() }
}
