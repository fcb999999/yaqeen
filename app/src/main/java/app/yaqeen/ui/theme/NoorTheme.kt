package app.yaqeen.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// ---------------------------------------------------------------- «نور»: كحلي وأزرق وعاجي
//
// مستوحى من تطبيقات المصاحف الرقمية الحديثة: واجهة كحلية/بيضاء نظيفة بلمسات زرقاء،
// وبطاقات ملوّنة مسطحة (أزرق، فيروزي، أخضر)، وصفحة مصحف عاجية بزخارف دقيقة.

/** نور (فاتح): خلفية بيضاء مائلة للزرقة، أزرق أساسي، كحلي ثانوي. */
internal val NoorLightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF2B4FB8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE5FB),
    onPrimaryContainer = Color(0xFF14244F),
    secondary = Color(0xFF24305E),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE3E8F6),
    onSecondaryContainer = Color(0xFF1A2550),
    tertiary = Color(0xFF0B7285),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFD3F1F5),
    onTertiaryContainer = Color(0xFF053E48),
    background = Color(0xFFF6F8FC),
    onBackground = Color(0xFF141A2E),
    surface = Color.White,
    onSurface = Color(0xFF141A2E),
    surfaceVariant = Color(0xFFEDF0F8),
    onSurfaceVariant = Color(0xFF465072),
    outline = Color(0xFF8C96B4),
    outlineVariant = Color(0xFFDDE2EE),
    error = Color(0xFFC62828),
    onError = Color.White,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFAFBFE),
    surfaceContainer = Color(0xFFF1F4FA),
    surfaceContainerHigh = Color(0xFFEBEFF8),
    surfaceContainerHighest = Color(0xFFE4E9F4),
)

/** نور (داكن): كحلي عميق كما في الصور، وأزرق فاتح للعناصر النشطة. */
internal val NoorDarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF9DB4F7),
    onPrimary = Color(0xFF0F1B45),
    primaryContainer = Color(0xFF34448A),
    onPrimaryContainer = Color(0xFFDCE5FB),
    secondary = Color(0xFFB4C1E8),
    onSecondary = Color(0xFF1A2550),
    secondaryContainer = Color(0xFF34407A),
    onSecondaryContainer = Color(0xFFE3E8F6),
    tertiary = Color(0xFF7FD6E2),
    onTertiary = Color(0xFF00363F),
    tertiaryContainer = Color(0xFF14505A),
    onTertiaryContainer = Color(0xFFD3F1F5),
    background = Color(0xFF1E284A),
    onBackground = Color(0xFFF1F3FA),
    surface = Color(0xFF26325E),
    onSurface = Color(0xFFF1F3FA),
    surfaceVariant = Color(0xFF2E3B6C),
    onSurfaceVariant = Color(0xFFC6CDE3),
    outline = Color(0xFF7A86AE),
    outlineVariant = Color(0xFF3A4677),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF601410),
    surfaceContainerLowest = Color(0xFF18203D),
    surfaceContainerLow = Color(0xFF212B52),
    surfaceContainer = Color(0xFF26325E),
    surfaceContainerHigh = Color(0xFF2C3867),
    surfaceContainerHighest = Color(0xFF334071),
)

/** نور: زوايا معتدلة (بطاقات 16dp) كما في الصور. */
internal val NoorShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

internal val NoorExtraLight = ExtraColors(Color(0xFF8E6F43), Color(0xFFF3EAD9), Color(0xFF18834A), Color(0xFFB7791F))
internal val NoorExtraDark = ExtraColors(Color(0xFFD9C29A), Color(0xFF3A3322), Color(0xFF7FD3A2), Color(0xFFE5B567))

/**
 * ألوان المجالات في «نور»: أزرق للقرآن والرئيسية، فيروزي للأذكار، أخضر للصلاة، نيلي للمهام،
 * أزرق سماوي للتقويم، كحلي للرحلة، رمادي-أزرق للإعدادات. النص الأبيض بتباين ≥ 4.5 على طرفي كل تدرّج.
 */
internal object NoorPalettes {
    private val White = Color.White

    private fun p(accent: Long, end: Long, container: Long, onContainer: Long) =
        AreaPalette(Color(accent), White, Color(end), Color(container), Color(onContainer))

    val light: Map<Hue, AreaPalette> = mapOf(
        Hue.VIOLET to p(0xFF1E5BC0, 0xFF174A9E, 0xFFDDE8FA, 0xFF123C7A),
        Hue.AMBER to p(0xFF0B7285, 0xFF085C6C, 0xFFD3F1F5, 0xFF053E48),
        Hue.TEAL to p(0xFF18834A, 0xFF126B3C, 0xFFD9F2E3, 0xFF0B4A29),
        Hue.CORAL to p(0xFF4347B8, 0xFF353894, 0xFFE2E3FA, 0xFF262A78),
        Hue.GREEN to p(0xFF1F6FB2, 0xFF185A91, 0xFFDCEBF8, 0xFF113F66),
        Hue.PINK to p(0xFF24305E, 0xFF1A2448, 0xFFE3E8F6, 0xFF1A2550),
        Hue.BLUE to p(0xFF3D4A7A, 0xFF2F3A62, 0xFFE4E8F2, 0xFF27315A),
    )

    val dark: Map<Hue, AreaPalette> = mapOf(
        Hue.VIOLET to p(0xFF1E5BC0, 0xFF174A9E, 0xFF22386E, 0xFFC4D6FA),
        Hue.AMBER to p(0xFF0B7285, 0xFF085C6C, 0xFF143F4A, 0xFFA7E6EF),
        Hue.TEAL to p(0xFF18834A, 0xFF126B3C, 0xFF16402D, 0xFFA8E6C3),
        Hue.CORAL to p(0xFF4347B8, 0xFF353894, 0xFF2B2E6E, 0xFFCDCFFA),
        Hue.GREEN to p(0xFF1F6FB2, 0xFF185A91, 0xFF1B3B5E, 0xFFBBD9F5),
        Hue.PINK to p(0xFF3A4A8A, 0xFF2C3A70, 0xFF2E3B6C, 0xFFDCE2F6),
        Hue.BLUE to p(0xFF3D4A7A, 0xFF2F3A62, 0xFF2E3B6C, 0xFFD6DCEE),
    )
}
