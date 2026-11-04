package app.yaqeen.feature.quran

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.ui.PlaceholderScreen
import app.yaqeen.ui.navigation.Routes

/** نقطة دخول ميزة القرآن الكريم (يستبدلها منفذ الميزة). */
fun NavGraphBuilder.quranGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.QURAN_HOME, route = Routes.QURAN) {
        composable(Routes.QURAN_HOME) { PlaceholderScreen("القرآن الكريم") }
    }
}
