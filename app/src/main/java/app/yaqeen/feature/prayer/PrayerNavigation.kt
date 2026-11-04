package app.yaqeen.feature.prayer

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen
import app.yaqeen.ui.navigation.Routes

/** نقطة دخول الصلاة والسنن والمواقيت والقبلة (يستبدلها منفذ الميزة). */
fun NavGraphBuilder.prayerGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.PRAYER_HOME, route = Routes.PRAYER) {
        composable(Routes.PRAYER_HOME) { PlaceholderScreen("الصلاة") }
    }
}
