package app.yaqeen.feature.quran.reader

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
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
    val layout = combine(observe(QuranKeys.PAGE_MARGIN), observe(Settings.THEME_MODE)) { m, t -> m to t }
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

    val all = listOf(Ivory, Sepia, Gray, Night)

    fun label(key: String): String = when (key) {
        "sepia" -> "ورقي"
        "gray" -> "رمادي"
        "night" -> "ليلي"
        else -> "افتراضي"
    }

    /**
     * "ivory" هو الخيار الافتراضي المتكيّف: عاجي في المظهر الفاتح، وليلي في المظهر الداكن
     * (حتى لا يُفاجَأ من اختار المظهر الداكن بصفحة بيضاء)؛ وبقية الخيارات تُطبَّق كما اختارها المستخدم.
     */
    fun resolve(background: String, appIsDark: Boolean): ReaderPalette = when (background) {
        "sepia" -> Sepia
        "gray" -> Gray
        "night" -> Night
        else -> if (appIsDark) Night else Ivory
    }
}
