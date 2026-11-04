package app.yaqeen.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------- مخططات ألوان «حياة»

/** حياة (فاتح): خلفية لافندر خفيفة، أسطح بيضاء، بنفسجي أساسي، فيروزي وعنبري ثانويان. */
internal val HayatLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF6C4CF1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E0FF),
    onPrimaryContainer = Color(0xFF2A1A8A),
    secondary = Color(0xFF0A7F8A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2F3F6),
    onSecondaryContainer = Color(0xFF064E56),
    tertiary = Color(0xFF8A5A00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE9B8),
    onTertiaryContainer = Color(0xFF4A3000),
    background = Color(0xFFF4F3FF),
    onBackground = Color(0xFF1C1936),
    surface = Color.White,
    onSurface = Color(0xFF1C1936),
    surfaceVariant = Color(0xFFEAE7FB),
    onSurfaceVariant = Color(0xFF4D4973),
    outline = Color(0xFF7D78A8),
    outlineVariant = Color(0xFFDCD8F3),
    error = Color(0xFFC62828),
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAF9FF),
    surfaceContainer = Color(0xFFF1EFFC),
    surfaceContainerHigh = Color(0xFFEAE7FA),
    surfaceContainerHighest = Color(0xFFE3E0F6),
)

/** حياة (داكن): نيلي عميق مع الألوان نفسها أفتح قليلًا. */
internal val HayatDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFFB7A6FF),
    onPrimary = Color(0xFF1F1066),
    primaryContainer = Color(0xFF3A2F85),
    onPrimaryContainer = Color(0xFFE5DEFF),
    secondary = Color(0xFF5ED6E0),
    onSecondary = Color(0xFF00363B),
    secondaryContainer = Color(0xFF14535A),
    onSecondaryContainer = Color(0xFFC6F4F8),
    tertiary = Color(0xFFFFC85C),
    onTertiary = Color(0xFF3D2800),
    tertiaryContainer = Color(0xFF55421F),
    onTertiaryContainer = Color(0xFFFFE9B8),
    background = Color(0xFF12102B),
    onBackground = Color(0xFFEEEAFF),
    surface = Color(0xFF1D1A3D),
    onSurface = Color(0xFFEEEAFF),
    surfaceVariant = Color(0xFF27235A),
    onSurfaceVariant = Color(0xFFBDB8DD),
    outline = Color(0xFF8480B5),
    outlineVariant = Color(0xFF34306A),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF601410),
    surfaceContainerLowest = Color(0xFF0E0C23),
    surfaceContainerLow = Color(0xFF171435),
    surfaceContainer = Color(0xFF1D1A3D),
    surfaceContainerHigh = Color(0xFF25214D),
    surfaceContainerHighest = Color(0xFF2E2A5E),
)

/** حياة: زوايا أكبر (البطاقات 28dp عبر shapes.medium). */
internal val HayatShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(28.dp),
    large = RoundedCornerShape(32.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

internal val HayatExtraLight = ExtraColors(Color(0xFF8A5A00), Color(0xFFFFE9B8), Color(0xFF12804A), Color(0xFFA85D00))
internal val HayatExtraDark = ExtraColors(Color(0xFFFFC85C), Color(0xFF4A3510), Color(0xFF6FDDA0), Color(0xFFFFB347))

// ---------------------------------------------------------------- ألوان المجالات

/** الألوان الأساسية في «حياة»؛ لكل مجال لون واحد. */
enum class Hue { VIOLET, TEAL, AMBER, CORAL, GREEN, PINK, BLUE }

/** مجالات التطبيق، لكل منها لون: القرآن بنفسجي، الأذكار عنبري، الصلاة فيروزي، المهام مرجاني، التقويم أخضر، الرحلة وردي، الإعدادات أزرق. */
enum class Area(val hue: Hue) {
    HOME(Hue.VIOLET),
    QURAN(Hue.VIOLET),
    ADHKAR(Hue.AMBER),
    PRAYER(Hue.TEAL),
    TASKS(Hue.CORAL),
    CALENDAR(Hue.GREEN),
    JOURNEY(Hue.PINK),
    SETTINGS(Hue.BLUE),
}

/**
 * ألوان مجال واحد:
 *  - [accent] → [gradientEnd]: تدرّج البطاقات والبلاطات (النص عليه بلون [onAccent]، تباين ≥ 4.5 على الطرفين).
 *  - [container]/[onContainer]: خلفية ناعمة لشارة الأيقونة ولونها.
 */
@Immutable
class AreaPalette(
    val accent: Color,
    val onAccent: Color,
    val gradientEnd: Color,
    val container: Color,
    val onContainer: Color,
)

/**
 * ألوان إضافية حسب النمط (CompositionLocal: [LocalYaqeenExtras]).
 * في classic تُشتق كلها من المخطط الحالي فلا يتغير شكل أي شاشة.
 */
@Immutable
class YaqeenExtras(
    val style: String,
    val dark: Boolean,
    private val palettes: Map<Hue, AreaPalette>,
    /** شريط التنقل العائم (حياة فقط). */
    val navContainer: Color,
    val onNav: Color,
    val navSelected: Color,
    val onNavSelected: Color,
) {
    val isHayat: Boolean get() = style == ThemeStyle.HAYAT

    fun hue(h: Hue): AreaPalette = palettes.getValue(h)
    fun area(a: Area): AreaPalette = hue(a.hue)

    val home: AreaPalette get() = area(Area.HOME)
    val quran: AreaPalette get() = area(Area.QURAN)
    val adhkar: AreaPalette get() = area(Area.ADHKAR)
    val prayer: AreaPalette get() = area(Area.PRAYER)
    val tasks: AreaPalette get() = area(Area.TASKS)
    val calendar: AreaPalette get() = area(Area.CALENDAR)
    val journey: AreaPalette get() = area(Area.JOURNEY)
    val settings: AreaPalette get() = area(Area.SETTINGS)

    companion object {
        fun classic(scheme: ColorScheme, dark: Boolean): YaqeenExtras {
            val p = AreaPalette(scheme.primary, scheme.onPrimary, scheme.primary, scheme.primaryContainer, scheme.onPrimaryContainer)
            return YaqeenExtras(
                ThemeStyle.CLASSIC, dark, Hue.entries.associateWith { p },
                navContainer = scheme.surface, onNav = scheme.onSurfaceVariant,
                navSelected = scheme.secondaryContainer, onNavSelected = scheme.onSecondaryContainer,
            )
        }

        fun hayat(dark: Boolean): YaqeenExtras = YaqeenExtras(
            ThemeStyle.HAYAT, dark, if (dark) HayatPalettes.dark else HayatPalettes.light,
            navContainer = if (dark) Color(0xFF4A3BB0) else Color(0xFF5A3BDD),
            onNav = Color.White,
            navSelected = Color.White,
            onNavSelected = if (dark) Color(0xFF4A3BB0) else Color(0xFF5A3BDD),
        )

        fun of(style: String?, dark: Boolean, scheme: ColorScheme): YaqeenExtras =
            if (ThemeStyle.isHayat(style)) hayat(dark) else classic(scheme, dark)
    }
}

internal object HayatPalettes {
    private val White = Color.White
    private val Ink = Color(0xFF3D2500)

    private fun p(accent: Long, end: Long, on: Color, container: Long, onContainer: Long) =
        AreaPalette(Color(accent), on, Color(end), Color(container), Color(onContainer))

    val light: Map<Hue, AreaPalette> = mapOf(
        Hue.VIOLET to p(0xFF6C4CF1, 0xFF4B2FC9, White, 0xFFEAE5FF, 0xFF3A22A8),
        Hue.TEAL to p(0xFF0A7F8A, 0xFF066570, White, 0xFFD6F3F6, 0xFF064E56),
        Hue.AMBER to p(0xFFFFB93B, 0xFFFF9F1C, Ink, 0xFFFFF0CC, 0xFF6B4300),
        Hue.CORAL to p(0xFFD43B30, 0xFFB92E25, White, 0xFFFFE3DF, 0xFF9B2218),
        Hue.GREEN to p(0xFF12804A, 0xFF0B6A3C, White, 0xFFDAF5E6, 0xFF0B5A33),
        Hue.PINK to p(0xFFC42E97, 0xFFA5217C, White, 0xFFFFE0F3, 0xFF8F1C6B),
        Hue.BLUE to p(0xFF2F63DB, 0xFF2450BD, White, 0xFFDDE8FF, 0xFF1B3F9E),
    )

    /** نفس تدرجات البلاطات (نص أبيض/داكن مضمون)، وحاويات ناعمة داكنة للشارات. */
    val dark: Map<Hue, AreaPalette> = mapOf(
        Hue.VIOLET to p(0xFF6C4CF1, 0xFF4B2FC9, White, 0xFF2E2766, 0xFFD9D0FF),
        Hue.TEAL to p(0xFF0A7F8A, 0xFF066570, White, 0xFF0F3B40, 0xFF9FE8EE),
        Hue.AMBER to p(0xFFFFB93B, 0xFFFF9F1C, Ink, 0xFF4A3510, 0xFFFFD98A),
        Hue.CORAL to p(0xFFD43B30, 0xFFB92E25, White, 0xFF4A2220, 0xFFFFB8B0),
        Hue.GREEN to p(0xFF12804A, 0xFF0B6A3C, White, 0xFF123B28, 0xFFA5E8C3),
        Hue.PINK to p(0xFFC42E97, 0xFFA5217C, White, 0xFF4A1D3C, 0xFFFFB8E4),
        Hue.BLUE to p(0xFF2F63DB, 0xFF2450BD, White, 0xFF1A2E5C, 0xFFB3C9FF),
    )
}

val LocalYaqeenExtras = staticCompositionLocalOf { YaqeenExtras.classic(ClassicLightColors, false) }
