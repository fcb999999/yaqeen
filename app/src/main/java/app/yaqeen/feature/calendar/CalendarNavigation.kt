package app.yaqeen.feature.calendar

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen
import app.yaqeen.ui.navigation.Routes

/** نقطة دخول التقويم (يستبدلها منفذ الميزة). */
fun NavGraphBuilder.calendarGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.CALENDAR_HOME, route = Routes.CALENDAR) {
        composable(Routes.CALENDAR_HOME) { PlaceholderScreen("التقويم") }
    }
}
