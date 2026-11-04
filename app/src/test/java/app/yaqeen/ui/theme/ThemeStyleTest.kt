package app.yaqeen.ui.theme

import androidx.compose.ui.graphics.Color
import app.yaqeen.data.settings.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeStyleTest {
    @Test fun settingKeyDefaultsToClassic() {
        assertEquals("classic", Settings.THEME_STYLE.default)
        assertEquals("user:theme_style", Settings.THEME_STYLE.id)
        // مستقل عن مفتاح وضع الفاتح/الداكن
        assertNotEquals(Settings.THEME_MODE.id, Settings.THEME_STYLE.id)
    }

    @Test fun settingRoundTrips() {
        for (v in ThemeStyle.ALL) {
            assertEquals(v, Settings.THEME_STYLE.decode(Settings.THEME_STYLE.encode(v)))
        }
    }

    @Test fun normalizeKnownValues() {
        assertEquals(ThemeStyle.CLASSIC, ThemeStyle.normalize("classic"))
        assertEquals(ThemeStyle.HAYAT, ThemeStyle.normalize("hayat"))
        assertTrue(ThemeStyle.isHayat("hayat"))
        assertFalse(ThemeStyle.isHayat("classic"))
    }

    @Test fun unknownValuesFallBackToClassic() {
        for (raw in listOf(null, "", " ", "Hayat", "HAYAT", "hayat ", "dark", "system", "x", "٠")) {
            assertEquals("raw=$raw", ThemeStyle.CLASSIC, ThemeStyle.normalize(raw))
            assertFalse("raw=$raw", ThemeStyle.isHayat(raw))
        }
        // قيمة محفوظة غير معروفة (مثلًا من إصدار أحدث) تقرأ كـ classic
        assertEquals(ThemeStyle.CLASSIC, ThemeStyle.normalize(Settings.THEME_STYLE.decode("neon")))
    }

    @Test fun themeModeResolution() {
        assertTrue(ThemeMode.isDark("dark", systemDark = false))
        assertFalse(ThemeMode.isDark("light", systemDark = true))
        assertTrue(ThemeMode.isDark("system", systemDark = true))
        assertFalse(ThemeMode.isDark("system", systemDark = false))
        // القيمة المجهولة = حسب النظام (السلوك القديم نفسه)
        assertTrue(ThemeMode.isDark("???", systemDark = true))
        assertFalse(ThemeMode.isDark(null, systemDark = false))
    }

    @Test fun schemeSelectionCoversAllFourCombinations() {
        assertSame(ClassicLightColors, YaqeenSchemes.scheme("classic", false))
        assertSame(ClassicDarkColors, YaqeenSchemes.scheme("classic", true))
        assertSame(HayatLightColors, YaqeenSchemes.scheme("hayat", false))
        assertSame(HayatDarkColors, YaqeenSchemes.scheme("hayat", true))
        // المجهول يعطي classic
        assertSame(ClassicLightColors, YaqeenSchemes.scheme("whatever", false))
        assertSame(ClassicDarkColors, YaqeenSchemes.scheme(null, true))
    }

    @Test fun classicExtrasMatchTheOriginalValues() {
        val l = YaqeenSchemes.extraColors("classic", false)
        assertEquals(Color(0xFF8E6F43), l.gold)
        assertEquals(Color(0xFF2E7D57), l.success)
        val d = YaqeenSchemes.extraColors("classic", true)
        assertEquals(Color(0xFFD4B98F), d.gold)
        assertEquals(Color(0xFF7FC8A0), d.success)
    }

    @Test fun classicAreaPaletteDerivesFromTheScheme() {
        for (dark in listOf(false, true)) {
            val scheme = YaqeenSchemes.scheme("classic", dark)
            val ex = YaqeenExtras.of("classic", dark, scheme)
            assertFalse(ex.isHayat)
            for (area in Area.entries) {
                val p = ex.area(area)
                assertEquals(scheme.primary, p.accent)
                assertEquals(scheme.onPrimary, p.onAccent)
                assertEquals(scheme.primaryContainer, p.container)
                assertEquals(scheme.onPrimaryContainer, p.onContainer)
            }
        }
    }

    @Test fun hayatAreasHaveDistinctColours() {
        val ex = YaqeenExtras.of("hayat", false, YaqeenSchemes.scheme("hayat", false))
        assertTrue(ex.isHayat)
        val accents = listOf(ex.quran, ex.adhkar, ex.prayer, ex.tasks, ex.calendar, ex.journey, ex.settings).map { it.accent }
        assertEquals(accents.size, accents.toSet().size)
        // لكل لون أساسي لوحة في الفاتح والداكن
        for (dark in listOf(false, true)) {
            val x = YaqeenExtras.hayat(dark)
            for (h in Hue.entries) x.hue(h)
        }
    }
}
