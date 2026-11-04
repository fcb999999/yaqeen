package app.yaqeen.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** تباين WCAG AA (≥ 4.5 للنص العادي) لألوان النمطين في الفاتح والداكن. */
class ThemeContrastTest {
    private val combos = listOf(
        Triple("classic", false, "classic-light"),
        Triple("classic", true, "classic-dark"),
        Triple("hayat", false, "hayat-light"),
        Triple("hayat", true, "hayat-dark"),
    )

    private fun check(name: String, fg: Color, bg: Color, min: Double = Contrast.AA_TEXT) {
        val r = Contrast.ratio(fg, bg)
        assertTrue("$name: التباين %.2f أقل من %.1f".format(r, min), r >= min)
    }

    @Test fun contrastMathIsCorrect() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, Contrast.ratio(Color.White, Color.White), 0.001)
        // مثال معروف: #767676 على الأبيض ≈ 4.54
        assertEquals(4.54, Contrast.ratio(Color(0xFF767676), Color.White), 0.02)
    }

    @Test fun corePairsPassAAInAllFourCombinations() {
        for ((style, dark, tag) in combos) {
            val s = YaqeenSchemes.scheme(style, dark)
            check("$tag onPrimary/primary", s.onPrimary, s.primary)
            check("$tag onSurface/surface", s.onSurface, s.surface)
            check("$tag onBackground/background", s.onBackground, s.background)
        }
    }

    @Test fun hayatSchemePairsPassAA() {
        for (dark in listOf(false, true)) {
            val tag = if (dark) "hayat-dark" else "hayat-light"
            val s = YaqeenSchemes.scheme("hayat", dark)
            check("$tag primary/background", s.primary, s.background)
            check("$tag primary/surface", s.primary, s.surface)
            check("$tag onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
            check("$tag onSecondary/secondary", s.onSecondary, s.secondary)
            check("$tag onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
            check("$tag onTertiary/tertiary", s.onTertiary, s.tertiary)
            check("$tag onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
            check("$tag onError/error", s.onError, s.error)
            check("$tag error/surface", s.error, s.surface)
            check("$tag onSurfaceVariant/surface", s.onSurfaceVariant, s.surface)
            check("$tag onSurfaceVariant/background", s.onSurfaceVariant, s.background)
            check("$tag onSurfaceVariant/surfaceVariant", s.onSurfaceVariant, s.surfaceVariant)
            check("$tag onSurface/surfaceVariant", s.onSurface, s.surfaceVariant)
            check("$tag onSurface/surfaceContainerHigh", s.onSurface, s.surfaceContainerHigh)
            // الحدود الأمامية المستعملة كنص ثانوي أحيانًا: 3:1 على الأقل
            check("$tag outline/surface", s.outline, s.surface, 3.0)
        }
    }

    @Test fun hayatAreaPalettesPassAAOnBothGradientStops() {
        for (dark in listOf(false, true)) {
            val x = YaqeenExtras.hayat(dark)
            for (h in Hue.entries) {
                val p = x.hue(h)
                val tag = "hayat-${if (dark) "dark" else "light"} $h"
                check("$tag onAccent/accent", p.onAccent, p.accent)
                check("$tag onAccent/gradientEnd", p.onAccent, p.gradientEnd)
                check("$tag onContainer/container", p.onContainer, p.container)
            }
        }
    }

    @Test fun hayatFloatingNavPassesAA() {
        for (dark in listOf(false, true)) {
            val x = YaqeenExtras.hayat(dark)
            val tag = if (dark) "dark" else "light"
            check("nav $tag onNav/navContainer", x.onNav, x.navContainer)
            check("nav $tag onNavSelected/navSelected", x.onNavSelected, x.navSelected)
            // أبيض الدائرة المحددة على لون الشريط (للأيقونة 3:1 على الأقل)
            check("nav $tag navSelected/navContainer", x.navSelected, x.navContainer, 3.0)
        }
    }

    @Test fun hayatExtraColorsPassAAOnSurface() {
        for (dark in listOf(false, true)) {
            val s = YaqeenSchemes.scheme("hayat", dark)
            val e = YaqeenSchemes.extraColors("hayat", dark)
            val tag = if (dark) "dark" else "light"
            check("extra $tag gold/surface", e.gold, s.surface)
            check("extra $tag success/surface", e.success, s.surface)
            check("extra $tag warning/surface", e.warning, s.surface)
            check("extra $tag onSurface/goldContainer", s.onSurface, e.goldContainer)
            check("extra $tag onSurfaceVariant/goldContainer", s.onSurfaceVariant, e.goldContainer)
        }
    }
}
