package app.yaqeen.feature.prayer.times

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import app.yaqeen.AppContainer
import app.yaqeen.feature.prayer.adhan.AdhanSettingsScreen
import app.yaqeen.ui.navigation.Routes

/** مسارات إضافية لميزة المواقيت والأذان (غير الموجودة في Routes). */
object PrayerRoutes {
    /** اختيار الموقع: GPS أو مدينة يدوية. */
    const val LOCATION = "prayer/times/location"
    /** إعدادات حساب المواقيت (الطريقة، المذهب، التعديلات). */
    const val CALC_SETTINGS = "prayer/times/settings"
    /** إعدادات الأذان والتنبيهات والأصوات. */
    const val ADHAN_SETTINGS = "prayer/adhan/settings"
}

/**
 * يسجّل شاشات المواقيت والقبلة وإعدادات الأذان داخل رسم الصلاة (يستدعيه PrayerNavigation).
 */
fun NavGraphBuilder.prayerTimesGraph(nav: NavHostController, c: AppContainer) {
    composable(Routes.PRAYER_TIMES) { PrayerTimesScreen(nav) }
    composable(Routes.PRAYER_QIBLA) { QiblaScreen(nav) }
    composable(PrayerRoutes.LOCATION) { PrayerLocationScreen(nav) }
    composable(PrayerRoutes.CALC_SETTINGS) { PrayerCalcSettingsScreen(nav) }
    composable(PrayerRoutes.ADHAN_SETTINGS) { AdhanSettingsScreen(nav) }
}
