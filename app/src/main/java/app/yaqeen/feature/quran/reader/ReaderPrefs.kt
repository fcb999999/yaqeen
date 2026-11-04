package app.yaqeen.feature.quran.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import app.yaqeen.data.settings.BoolKey
import app.yaqeen.data.settings.IntKey
import app.yaqeen.data.settings.SettingsRepository
import app.yaqeen.data.settings.Settings
import app.yaqeen.domain.model.SettingScope
import app.yaqeen.ui.theme.YaqeenColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** مفاتيح إعدادات إضافية خاصة بقارئ المصحف (بقية المفاتيح في [Settings].QURAN_*). */
object QuranKeys {
    /** الهامش الأفقي للصفحة بوحدة dp. */
    val PAGE_MARGIN = IntKey(SettingScope.USER, "quran_page_margin", 16)

    /** تكبير الخط تلقائيًا ليملأ الصفحة الشاشات الكبيرة (اللوحية) بدل ترك فراغ أسفل الآيات. */
    val FIT_PAGE = BoolKey(SettingScope.USER, "quran_fit_page", true)
}

/** تفضيلات القراءة كما هي في الإعدادات (بعد التقييد بالحدود المعقولة). */
@Immutable
data class ReaderPrefs(
    val fontSize: Float,
    val lineSpacing: Float,
    val background: String,
    val keepScreenOn: Boolean,
    /** فتح القارئ بوضع ملء الشاشة. */
    val startFullscreen: Boolean,
    val scrollSpeed: Int,
    val pageMargin: Int,
    val themeMode: String,
    /** ملاءمة حجم الخط للشاشات الكبيرة (تعمل فقط حين يكون عرض الشاشة ≥ ٦٠٠dp). */
    val fitPage: Boolean = true,
) {
    companion object {
        const val MIN_FONT = 20f
        const val MAX_FONT = 52f
        const val MIN_LINE = 1.4f
        const val MAX_LINE = 2.8f
        const val MIN_MARGIN = 6
        const val MAX_MARGIN = 40
    }
}

fun SettingsRepository.observeReaderPrefs(): Flow<ReaderPrefs> {
    val text = combine(
        observe(Settings.QURAN_FONT_SIZE),
        observe(Settings.QURAN_LINE_SPACING),
        observe(Settings.QURAN_BACKGROUND),
    ) { f, l, b -> Triple(f, l, b) }
    val behavior = combine(
        observe(Settings.QURAN_KEEP_SCREEN_ON),
        observe(Settings.QURAN_FULLSCREEN),
        observe(Settings.QURAN_SCROLL_SPEED),
    ) { k, fs, sp -> Triple(k, fs, sp) }
    val layout = combine(observe(QuranKeys.PAGE_MARGIN), observe(Settings.THEME_MODE), observe(QuranKeys.FIT_PAGE)) { m, t, f -> Triple(m, t, f) }
    return combine(text, behavior, layout) { t, b, l ->
        ReaderPrefs(
            fontSize = t.first.coerceIn(ReaderPrefs.MIN_FONT, ReaderPrefs.MAX_FONT),
            lineSpacing = t.second.coerceIn(ReaderPrefs.MIN_LINE, ReaderPrefs.MAX_LINE),
            background = t.third,
            keepScreenOn = b.first,
            startFullscreen = b.second,
            scrollSpeed = AutoScroll.clampSpeed(b.third),
            pageMargin = l.first.coerceIn(ReaderPrefs.MIN_MARGIN, ReaderPrefs.MAX_MARGIN),
            themeMode = l.second,
            fitPage = l.third,
        )
    }
}

/**
 * ألوان صفحة القراءة. خلفية القراءة اختيار صريح من المستخدم (عاجي/ورقي/رمادي/ليلي)، لذلك تُعرَّف هنا
 * ولا تتبع ألوان المظهر؛ أما الأشرطة والنوافذ فتستعمل MaterialTheme.
 */
@Immutable
class ReaderPalette(
    val key: String,
    val bg: Color,
    val text: Color,
    val accent: Color,
    val muted: Color,
    val isLight: Boolean,
)

object ReaderPalettes {
    private val Ivory = ReaderPalette("ivory", YaqeenColors.Ivory, Color(0xFF1A1F2B), Color(0xFF8E6F43), Color(0xFF6B7280), true)
    private val Sepia = ReaderPalette("sepia", Color(0xFFF3E7CE), Color(0xFF3B2A14), Color(0xFF8A5A22), Color(0xFF7A6A52), true)
    private val Gray = ReaderPalette("gray", Color(0xFFE3E5E8), Color(0xFF1F232B), Color(0xFF3F5A86), Color(0xFF5B6270), true)
    private val Night = ReaderPalette("night", YaqeenColors.Night, Color(0xFFE7EAF0), Color(0xFFD4B98F), Color(0xFF9AA3B5), false)

    // خلفيات بألوان المصاحف الرقمية الحديثة (كريمي، أخضر، أزرق، كحلي، أسود)
    private val Cream = ReaderPalette("cream", Color(0xFFFFF8EE), Color(0xFF231F1A), Color(0xFF9C7B4C), Color(0xFF7E7262), true)
    private val Green = ReaderPalette("green", Color(0xFFEEF5EA), Color(0xFF1C2A1B), Color(0xFF4F8A4A), Color(0xFF66775F), true)
    private val Blue = ReaderPalette("blue", Color(0xFFECF2FA), Color(0xFF18212F), Color(0xFF4C74B5), Color(0xFF5F6E86), true)
    private val Navy = ReaderPalette("navy", Color(0xFF1E284A), Color(0xFFF2ECDF), Color(0xFF9DB3EA), Color(0xFFA9B2CC), false)
    private val Black = ReaderPalette("black", Color(0xFF000000), Color(0xFFD9D9D9), Color(0xFF9A9A9A), Color(0xFF8A8A8A), false)

    val all = listOf(Ivory, Cream, Sepia, Green, Blue, Gray, Navy, Night, Black)

    fun label(key: String): String = when (key) {
        "cream" -> "كريمي"
        "sepia" -> "ورقي"
        "green" -> "أخضر"
        "blue" -> "أزرق"
        "gray" -> "رمادي"
        "navy" -> "كحلي"
        "night" -> "ليلي"
        "black" -> "أسود"
        else -> "افتراضي"
    }

    /**
     * "ivory" هو الخيار الافتراضي المتكيّف: عاجي في المظهر الفاتح، وليلي في المظهر الداكن
     * (حتى لا يُفاجَأ من اختار المظهر الداكن بصفحة بيضاء)؛ وفي نمط «نور»: كريمي نهارًا وكحلي ليلًا.
     * بقية الخيارات تُطبَّق كما اختارها المستخدم.
     */
    fun resolve(background: String, appIsDark: Boolean, noor: Boolean = false): ReaderPalette = when (background) {
        "cream" -> Cream
        "sepia" -> Sepia
        "green" -> Green
        "blue" -> Blue
        "gray" -> Gray
        "navy" -> Navy
        "night" -> Night
        "black" -> Black
        else -> when {
            noor -> if (appIsDark) Navy else Cream
            appIsDark -> Night
            else -> Ivory
        }
    }
}
