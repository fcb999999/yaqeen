package app.yaqeen.ui.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * نمط التطبيق البصري، مستقل عن وضع الفاتح/الداكن:
 *  - classic (كلاسيكي): الأزرق الملكي والعاجي الهادئ — الشكل الأصلي للتطبيق.
 *  - hayat (حياة): ألوان نابضة وبطاقات مدوّرة وشريط تنقل عائم.
 * القيم المجهولة أو الفارغة تعود إلى classic دائمًا.
 */
object ThemeStyle {
    const val CLASSIC = "classic"
    const val HAYAT = "hayat"

    /** نور: كحلي وأزرق وعاجي، بزخارف المصحف المطبوع. */
    const val NOOR = "noor"

    val ALL: List<String> = listOf(CLASSIC, HAYAT, NOOR)

    fun normalize(raw: String?): String = when (raw) {
        HAYAT -> HAYAT
        NOOR -> NOOR
        else -> CLASSIC
    }

    fun isHayat(raw: String?): Boolean = normalize(raw) == HAYAT

    fun isNoor(raw: String?): Boolean = normalize(raw) == NOOR
}

/** وضع المظهر المحفوظ في الإعدادات: system | light | dark (المجهول = system). */
object ThemeMode {
    fun isDark(mode: String?, systemDark: Boolean): Boolean = when (mode) {
        "dark" -> true
        "light" -> false
        else -> systemDark
    }
}

/** حساب نسبة التباين وفق WCAG 2.x (للاختبارات والتحقق من إمكانية القراءة). */
object Contrast {
    private fun channel(v: Float): Double {
        val c = v.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }

    fun luminance(c: Color): Double =
        0.2126 * channel(c.red) + 0.7152 * channel(c.green) + 0.0722 * channel(c.blue)

    /** نسبة التباين بين لونين (1..21)؛ AA للنص العادي = 4.5. */
    fun ratio(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    const val AA_TEXT = 4.5
}
