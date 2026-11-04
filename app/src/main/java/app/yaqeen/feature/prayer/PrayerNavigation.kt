package app.yaqeen.feature.prayer

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.feature.prayer.times.prayerTimesGraph
import app.yaqeen.feature.prayer.tracking.PrayerHomeScreen
import app.yaqeen.feature.prayer.tracking.PrayerRoutes
import app.yaqeen.feature.prayer.tracking.PrayerStatsScreen
import app.yaqeen.feature.prayer.tracking.SunnahManageScreen
import app.yaqeen.ui.navigation.Routes

/**
 * نقطة دخول الصلاة (يملكها منفذ "تسجيل الصلوات والسنن"): الشاشة الرئيسية للتبويب + الإحصاءات + إدارة السنن.
 * شاشات المواقيت والقبلة والأذان يسجّلها منفذ "المواقيت" عبر [prayerTimesGraph].
 */
fun NavGraphBuilder.prayerGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.PRAYER_HOME, route = Routes.PRAYER) {
        composable(Routes.PRAYER_HOME) { PrayerHomeScreen(nav, c) }
        composable(Routes.PRAYER_STATS) { PrayerStatsScreen(nav, c) }
        composable(PrayerRoutes.SUNNAH) { SunnahManageScreen(nav, c) }
        prayerTimesGraph(nav, c)
    }
}
