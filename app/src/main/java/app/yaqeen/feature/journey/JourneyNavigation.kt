package app.yaqeen.feature.journey

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import app.yaqeen.AppContainer
import app.yaqeen.ui.navigation.Routes

/** رحلتي مع يقين: الإحصائيات الشخصية (تُفتح من تبويب "المزيد"). */
fun NavGraphBuilder.journeyGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.JOURNEY_HOME, route = Routes.JOURNEY) {
        composable(Routes.JOURNEY_HOME) { JourneyScreen(nav, c) }
    }
}
