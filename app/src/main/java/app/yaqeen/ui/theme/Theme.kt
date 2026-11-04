package app.yaqeen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.yaqeen.R

/** ألوان الهوية: أزرق ملكي داكن + أزرق متوسط + ذهبي هادئ على خلفية عاجية/رمادي فاتح. */
object YaqeenColors {
    val RoyalBlue = Color(0xFF1B3F6B)
    val MidBlue = Color(0xFF4A78A8)
    val Gold = Color(0xFFC2A27A)
    val Ivory = Color(0xFFFAF8F4)
    val LightGray = Color(0xFFECEEF2)
    val Night = Color(0xFF0E1726)
}

internal val ClassicLightColors: ColorScheme = lightColorScheme(
    primary = YaqeenColors.RoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE6F3),
    onPrimaryContainer = Color(0xFF0F2745),
    secondary = YaqeenColors.MidBlue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3ECF6),
    onSecondaryContainer = Color(0xFF14304F),
    tertiary = Color(0xFF8E6F43),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF1E4CF),
    onTertiaryContainer = Color(0xFF3B2C14),
    background = YaqeenColors.Ivory,
    onBackground = Color(0xFF1A1F2B),
    surface = Color.White,
    onSurface = Color(0xFF1A1F2B),
    surfaceVariant = YaqeenColors.LightGray,
    onSurfaceVariant = Color(0xFF4A5262),
    outline = Color(0xFFB9C0CC),
    outlineVariant = Color(0xFFDDE1E8),
    error = Color(0xFFB3261E),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F4EF),
    surfaceContainer = Color(0xFFF1EFEA),
    surfaceContainerHigh = Color(0xFFECEAE5),
    surfaceContainerHighest = Color(0xFFE6E4DF),
)

internal val ClassicDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF9DB9E0),
    onPrimary = Color(0xFF0E2A4F),
    primaryContainer = Color(0xFF1B3F6B),
    onPrimaryContainer = Color(0xFFDCE6F3),
    secondary = Color(0xFF8DB0D6),
    onSecondary = Color(0xFF0F2745),
    secondaryContainer = Color(0xFF22456C),
    onSecondaryContainer = Color(0xFFDCE6F3),
    tertiary = Color(0xFFD4B98F),
    onTertiary = Color(0xFF3B2C14),
    tertiaryContainer = Color(0xFF55421F),
    onTertiaryContainer = Color(0xFFF1E4CF),
    background = YaqeenColors.Night,
    onBackground = Color(0xFFE7EAF0),
    surface = Color(0xFF15213A),
    onSurface = Color(0xFFE7EAF0),
    surfaceVariant = Color(0xFF1F2C48),
    onSurfaceVariant = Color(0xFFB7BFCD),
    outline = Color(0xFF5C677C),
    outlineVariant = Color(0xFF2B3855),
    error = Color(0xFFF2B8B5),
    surfaceContainerLowest = Color(0xFF0B1220),
    surfaceContainerLow = Color(0xFF121D33),
    surfaceContainer = Color(0xFF17233C),
    surfaceContainerHigh = Color(0xFF1C2943),
    surfaceContainerHighest = Color(0xFF22304C),
)

object YaqeenFonts {
    val Ui = FontFamily(
        Font(R.font.plex_arabic_regular, FontWeight.Normal),
        Font(R.font.plex_arabic_medium, FontWeight.Medium),
        Font(R.font.plex_arabic_bold, FontWeight.Bold),
    )

    /** للأذكار والأدعية: خط أميري المتوازن مع التشكيل. */
    val Adhkar = FontFamily(
        Font(R.font.amiri_regular, FontWeight.Normal),
        Font(R.font.amiri_bold, FontWeight.Bold),
    )

    /** للنص القرآني فقط: خط أميري القرآني المصمم لرسم المصحف. */
    val Quran = FontFamily(Font(R.font.amiri_quran, FontWeight.Normal))
}

private fun uiStyle(size: Int, line: Int, weight: FontWeight = FontWeight.Normal) = TextStyle(
    fontFamily = YaqeenFonts.Ui, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp,
    textDirection = TextDirection.Rtl, lineBreak = LineBreak.Paragraph,
)

private val YaqeenTypography = Typography(
    displayLarge = uiStyle(48, 60, FontWeight.Bold),
    displayMedium = uiStyle(40, 50, FontWeight.Bold),
    displaySmall = uiStyle(32, 42, FontWeight.Bold),
    headlineLarge = uiStyle(28, 38, FontWeight.Bold),
    headlineMedium = uiStyle(24, 34, FontWeight.Bold),
    headlineSmall = uiStyle(20, 30, FontWeight.Bold),
    titleLarge = uiStyle(20, 30, FontWeight.Bold),
    titleMedium = uiStyle(16, 26, FontWeight.Medium),
    titleSmall = uiStyle(14, 22, FontWeight.Medium),
    bodyLarge = uiStyle(16, 26),
    bodyMedium = uiStyle(14, 22),
    bodySmall = uiStyle(12, 18),
    labelLarge = uiStyle(14, 20, FontWeight.Medium),
    labelMedium = uiStyle(12, 16, FontWeight.Medium),
    labelSmall = uiStyle(11, 16, FontWeight.Medium),
)

internal val YaqeenShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
)

/** ألوان إضافية للهوية خارج مخطط Material. */
@Immutable
data class ExtraColors(val gold: Color, val goldContainer: Color, val success: Color, val warning: Color)

val LocalExtraColors = staticCompositionLocalOf { ExtraColors(Color.Unspecified, Color.Unspecified, Color.Unspecified, Color.Unspecified) }

object YaqeenTheme {
    val extra: ExtraColors @Composable @ReadOnlyComposable get() = LocalExtraColors.current

    /** ألوان المجالات وشريط التنقل حسب النمط الحالي (classic/hayat). */
    val extras: YaqeenExtras @Composable @ReadOnlyComposable get() = LocalYaqeenExtras.current

    /** هل النمط الحالي «حياة»؟ */
    val isHayat: Boolean @Composable @ReadOnlyComposable get() = LocalYaqeenExtras.current.isHayat
}

/** اختيار المخطط والألوان الإضافية حسب (النمط × الفاتح/الداكن). دوال خالصة قابلة للاختبار. */
object YaqeenSchemes {
    fun scheme(style: String?, dark: Boolean): ColorScheme = when {
        ThemeStyle.isHayat(style) -> if (dark) HayatDarkColors else HayatLightColors
        else -> if (dark) ClassicDarkColors else ClassicLightColors
    }

    fun extraColors(style: String?, dark: Boolean): ExtraColors = when {
        ThemeStyle.isHayat(style) -> if (dark) HayatExtraDark else HayatExtraLight
        dark -> ExtraColors(Color(0xFFD4B98F), Color(0xFF3A2F1C), Color(0xFF7FC8A0), Color(0xFFE5B567))
        else -> ExtraColors(Color(0xFF8E6F43), Color(0xFFF1E4CF), Color(0xFF2E7D57), Color(0xFFB7791F))
    }

    fun shapes(style: String?): Shapes = if (ThemeStyle.isHayat(style)) HayatShapes else YaqeenShapes
}

/** أنماط نص القرآن والأذكار (حجم الخط يأتي من إعدادات المستخدم). */
object YaqeenTextStyles {
    fun quran(sizeSp: Float, lineSpacing: Float = 1.9f) = TextStyle(
        fontFamily = YaqeenFonts.Quran, fontSize = sizeSp.sp, lineHeight = (sizeSp * lineSpacing).sp,
        textAlign = TextAlign.Justify, textDirection = TextDirection.Rtl,
    )

    fun adhkar(sizeSp: Float, lineSpacing: Float = 1.9f) = TextStyle(
        fontFamily = YaqeenFonts.Adhkar, fontSize = sizeSp.sp, lineHeight = (sizeSp * lineSpacing).sp,
        textAlign = TextAlign.Center, textDirection = TextDirection.Rtl,
    )
}

/** وضع المظهر المحفوظ في الإعدادات: system | light | dark، ونمط التطبيق: classic | hayat (المجهول = classic). */
@Composable
fun YaqeenTheme(themeMode: String = "system", themeStyle: String = ThemeStyle.CLASSIC, content: @Composable () -> Unit) {
    val dark = ThemeMode.isDark(themeMode, isSystemInDarkTheme())
    val style = ThemeStyle.normalize(themeStyle)
    val scheme: ColorScheme = YaqeenSchemes.scheme(style, dark)
    val extra = YaqeenSchemes.extraColors(style, dark)
    val extras = remember(style, dark) { YaqeenExtras.of(style, dark, scheme) }
    // التطبيق عربي بالكامل: اتجاه RTL دائمًا.
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalExtraColors provides extra,
        LocalYaqeenExtras provides extras,
    ) {
        MaterialTheme(colorScheme = scheme, typography = YaqeenTypography, shapes = YaqeenSchemes.shapes(style), content = content)
    }
}
