package app.yaqeen.feature.tasks

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen
import app.yaqeen.ui.navigation.Routes

/** نقطة دخول المهام اليومية (يستبدلها منفذ الميزة). */
fun NavGraphBuilder.tasksGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.TASKS_HOME, route = Routes.TASKS) {
        composable(Routes.TASKS_HOME) { PlaceholderScreen("المهام") }
    }
}
