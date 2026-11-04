package app.yaqeen.feature.tasks

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import app.yaqeen.AppContainer
import app.yaqeen.ui.navigation.Routes
import java.time.LocalDate

/**
 * رسم المهام اليومية:
 *  - tasks/home: مهام اليوم مع التقدم   - tasks/day?date=: يوم محدد (سابق للعرض والتصحيح)
 *  - tasks/edit?id=&preset=: المحرر      - tasks/presets: المهام الجاهزة
 *  - tasks/day_summary?date=: ملخص اليوم الحي (يُفتح من إشعار نهاية اليوم)
 */
fun NavGraphBuilder.tasksGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.TASKS_HOME, route = Routes.TASKS) {
        composable(Routes.TASKS_HOME) { TasksHomeScreen(nav) }
        composable(
            TasksRoutes.DAY_PATTERN,
            arguments = listOf(navArgument("date") { type = NavType.StringType; defaultValue = TasksRoutes.NONE }),
        ) { entry ->
            TasksHomeScreen(nav, initialDate = parseRouteDate(entry.arguments?.getString("date")))
        }
        composable(
            TasksRoutes.EDITOR_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; defaultValue = TasksRoutes.NEW },
                navArgument("preset") { type = NavType.StringType; defaultValue = TasksRoutes.NONE },
            ),
        ) { entry ->
            TaskEditorScreen(
                nav,
                id = entry.arguments?.getString("id") ?: TasksRoutes.NEW,
                preset = entry.arguments?.getString("preset") ?: TasksRoutes.NONE,
            )
        }
        composable(TasksRoutes.PRESETS) { TaskPresetsScreen(nav) }
        composable(
            TasksRoutes.DAY_SUMMARY_PATTERN,
            arguments = listOf(navArgument("date") { type = NavType.StringType; defaultValue = TasksRoutes.NONE }),
        ) { entry ->
            DaySummaryScreen(nav, parseRouteDate(entry.arguments?.getString("date")) ?: c.clock.today())
        }
    }
}

/** يحلّل وسيط تاريخ ISO من المسار؛ القيمة غير الصالحة (أو "none") = null أي اليوم. */
internal fun parseRouteDate(raw: String?): LocalDate? = ArabicFormat.parseDate(raw)
