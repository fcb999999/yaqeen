package app.yaqeen.feature.adhkar

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen
import app.yaqeen.ui.navigation.Routes

/** نقطة دخول ميزة الأذكار (يستبدلها منفذ الميزة). */
fun NavGraphBuilder.adhkarGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.ADHKAR_HOME, route = Routes.ADHKAR) {
        composable(Routes.ADHKAR_HOME) { PlaceholderScreen("الأذكار") }
    }
}
