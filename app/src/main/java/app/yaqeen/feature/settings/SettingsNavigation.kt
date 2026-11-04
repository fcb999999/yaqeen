package app.yaqeen.feature.settings

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.feature.settings.updates.UpdatesScreen
import app.yaqeen.ui.navigation.Routes

/**
 * الإعدادات والحساب والإشعارات والتحديثات والخصوصية وإدارة البيانات.
 * التهيئة الأولى (Routes.ONBOARDING) مسجّلة خارج الرسم المتداخل لأن AppRoot يفتحها مباشرة بمسارها.
 */
fun NavGraphBuilder.settingsGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.SETTINGS_HOME, route = Routes.SETTINGS) {
        composable(Routes.SETTINGS_HOME) { SettingsHomeScreen(nav, c) }
        composable(Routes.SETTINGS_NOTIFICATIONS) { NotificationsSettingsScreen(nav, c) }
        composable(Routes.SETTINGS_ACCOUNT) { AccountScreen(nav, c) }
        composable(Routes.SETTINGS_UPDATES) { UpdatesScreen(nav, c) }
        composable(SettingsRoutes.PRIVACY) { PrivacyScreen(nav) }
        composable(SettingsRoutes.ABOUT) { AboutScreen(nav) }
        composable(SettingsRoutes.DATA) { DataManagementScreen(nav, c) }
        composable(SettingsRoutes.STORAGE) { StorageScreen(nav, c) }
    }
    composable(Routes.ONBOARDING) { OnboardingScreen(nav, c) }
}
