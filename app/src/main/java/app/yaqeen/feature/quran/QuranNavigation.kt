package app.yaqeen.feature.quran

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import app.yaqeen.AppContainer
import app.yaqeen.feature.quran.audio.QuranDownloadsScreen
import app.yaqeen.feature.quran.bookmarks.BookmarksScreen
import app.yaqeen.feature.quran.home.QuranHomeScreen
import app.yaqeen.feature.quran.home.QuranSettingsScreen
import app.yaqeen.feature.quran.index.QuranIndexScreen
import app.yaqeen.feature.quran.plans.QuranPlansScreen
import app.yaqeen.feature.quran.reader.QuranReaderScreen
import app.yaqeen.feature.quran.search.QuranSearchScreen
import app.yaqeen.ui.navigation.Routes

/** مسارات إضافية داخل رسم القرآن (المسارات الأساسية في [Routes]). */
object QuranRoutes {
    const val INDEX = "quran/index"
    const val SEARCH = "quran/search"
    const val SETTINGS = "quran/settings"
    const val BOOKMARKS = "quran/bookmarks"
    const val DOWNLOADS = "quran/downloads"
}

/** رسم ميزة القرآن الكريم: الرئيسية، القارئ، الفهرس، البحث، الإعدادات، العلامات، الأوراد، التنزيلات. */
fun NavGraphBuilder.quranGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.QURAN_HOME, route = Routes.QURAN) {
        composable(Routes.QURAN_HOME) { QuranHomeScreen(nav, c) }

        composable(
            Routes.QURAN_READER_PATTERN,
            arguments = listOf(
                navArgument("surah") { type = NavType.IntType; defaultValue = -1 },
                navArgument("ayah") { type = NavType.IntType; defaultValue = -1 },
                navArgument("page") { type = NavType.IntType; defaultValue = -1 },
            ),
        ) { entry ->
            val args = entry.arguments
            QuranReaderScreen(
                nav = nav,
                c = c,
                surah = args?.getInt("surah") ?: -1,
                ayah = args?.getInt("ayah") ?: -1,
                page = args?.getInt("page") ?: -1,
            )
        }

        composable(QuranRoutes.INDEX) { QuranIndexScreen(nav, c) }
        composable(QuranRoutes.SEARCH) { QuranSearchScreen(nav, c) }
        composable(QuranRoutes.SETTINGS) { QuranSettingsScreen(nav, c) }

        // شاشات يملكها منفذ الأوراد والعلامات والصوت والتفسير
        composable(QuranRoutes.BOOKMARKS) { BookmarksScreen(nav, c) }
        composable(
            Routes.QURAN_PLANS_PATTERN,
            arguments = listOf(navArgument("planId") { type = NavType.StringType; defaultValue = "" }),
        ) { entry ->
            val planId = entry.arguments?.getString("planId").orEmpty()
            QuranPlansScreen(nav, c, planId.ifBlank { null })
        }
        composable(QuranRoutes.DOWNLOADS) { QuranDownloadsScreen(nav, c) }
    }
}
