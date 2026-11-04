package app.yaqeen.feature.adhkar

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import app.yaqeen.AppContainer
import app.yaqeen.domain.model.AdhkarMarkType
import app.yaqeen.ui.containerViewModel
import app.yaqeen.ui.navigation.Routes

/** مسارات الأذكار الداخلية (غير المعرَّفة في Routes المشتركة). */
object AdhkarRoutes {
    const val SEARCH = "adhkar/search"
    const val FAVORITES = "adhkar/favorites"
    const val BOOKMARKS = "adhkar/bookmarks"
}

/** نقطة دخول ميزة الأذكار: الرسم الفرعي لتبويب الأذكار. */
fun NavGraphBuilder.adhkarGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.ADHKAR_HOME, route = Routes.ADHKAR) {
        composable(Routes.ADHKAR_HOME) {
            val vm = containerViewModel { AdhkarHomeViewModel(it) }
            AdhkarHomeScreen(
                vm = vm,
                onOpenCategory = { id, start -> nav.navigate(Routes.adhkarCategory(id, start)) },
                onSearch = { nav.navigate(AdhkarRoutes.SEARCH) },
                onFavorites = { nav.navigate(AdhkarRoutes.FAVORITES) },
                onBookmarks = { nav.navigate(AdhkarRoutes.BOOKMARKS) },
            )
        }
        composable(
            route = Routes.ADHKAR_CATEGORY_PATTERN,
            arguments = listOf(
                navArgument("categoryId") { type = NavType.StringType },
                navArgument("start") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { entry ->
            val id = entry.arguments?.getString("categoryId").orEmpty()
            val start = entry.arguments?.getInt("start", -1) ?: -1
            val vm = containerViewModel(key = "adhkar_category_$id") { AdhkarCategoryViewModel(it, id, start) }
            AdhkarCategoryScreen(vm = vm, onBack = { nav.popBackStack() })
        }
        composable(AdhkarRoutes.FAVORITES) {
            val vm = containerViewModel(key = "adhkar_favorites") { AdhkarMarksViewModel(it, AdhkarMarkType.FAVORITE) }
            AdhkarMarksScreen(vm, onBack = { nav.popBackStack() }, onOpen = { cat, idx -> nav.navigate(Routes.adhkarCategory(cat, idx)) })
        }
        composable(AdhkarRoutes.BOOKMARKS) {
            val vm = containerViewModel(key = "adhkar_bookmarks") { AdhkarMarksViewModel(it, AdhkarMarkType.BOOKMARK) }
            AdhkarMarksScreen(vm, onBack = { nav.popBackStack() }, onOpen = { cat, idx -> nav.navigate(Routes.adhkarCategory(cat, idx)) })
        }
        composable(AdhkarRoutes.SEARCH) {
            val vm = containerViewModel(key = "adhkar_search") { AdhkarSearchViewModel(it) }
            AdhkarSearchScreen(vm, onBack = { nav.popBackStack() }, onOpen = { cat, idx -> nav.navigate(Routes.adhkarCategory(cat, idx)) })
        }
    }
}
