package app.yaqeen.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import app.yaqeen.AppContainer
import app.yaqeen.data.settings.Settings

/** حدود مقياس خط الواجهة المسموح بها. */
const val UI_FONT_SCALE_MIN = 0.85f
const val UI_FONT_SCALE_MAX = 1.4f

/**
 * يطبّق إعداد "حجم خط الواجهة" (Settings.UI_FONT_SCALE) على كل ما بداخله بضرب fontScale النظام.
 * للدمج: لُفّ محتوى AppRoot به داخل YaqeenTheme:
 *     YaqeenTheme(themeMode) { ProvideUiFontScale(container) { ...Scaffold... } }
 */
@Composable
fun ProvideUiFontScale(container: AppContainer, content: @Composable () -> Unit) {
    val scale by container.settings.observe(Settings.UI_FONT_SCALE).collectAsState(initial = Settings.UI_FONT_SCALE.default)
    val base = LocalDensity.current
    val s = scale.coerceIn(UI_FONT_SCALE_MIN, UI_FONT_SCALE_MAX)
    CompositionLocalProvider(LocalDensity provides Density(base.density, base.fontScale * s)) { content() }
}
