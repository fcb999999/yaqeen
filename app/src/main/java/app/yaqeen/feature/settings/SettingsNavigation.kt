package app.yaqeen.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen
import app.yaqeen.ui.navigation.Routes

/** الإعدادات والحساب والإشعارات والتحديثات (يستبدلها منفذ الميزة). التهيئة الأولى مسجّلة بمسارها الخاص ONBOARDING. */
fun NavGraphBuilder.settingsGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.SETTINGS_HOME, route = Routes.SETTINGS) {
        composable(Routes.SETTINGS_HOME) { PlaceholderScreen("الإعدادات") }
    }
    composable(Routes.ONBOARDING) { PlaceholderScreen("مرحبًا بك في يقين") }
}
