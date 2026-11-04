package app.yaqeen.feature.prayer.times

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen
import app.yaqeen.ui.navigation.Routes

/**
 * يسجّل شاشات المواقيت والقبلة وإعدادات الأذان داخل رسم الصلاة (يستدعيه PrayerNavigation).
 * يملكه منفذ "المواقيت والقبلة والأذان". المسارات المطلوبة: Routes.PRAYER_TIMES, Routes.PRAYER_QIBLA
 * ويمكن إضافة مسارات فرعية تبدأ بـ "prayer/times/..." أو "prayer/adhan/...".
 */
fun NavGraphBuilder.prayerTimesGraph(nav: NavHostController, c: AppContainer) {
    composable(Routes.PRAYER_TIMES) { PlaceholderScreen("مواقيت الصلاة") }
    composable(Routes.PRAYER_QIBLA) { PlaceholderScreen("اتجاه القبلة") }
}
