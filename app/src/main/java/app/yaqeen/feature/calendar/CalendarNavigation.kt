package app.yaqeen.feature.calendar

import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.navArgument
import app.yaqeen.AppContainer
import app.yaqeen.feature.tasks.ArabicFormat
import app.yaqeen.ui.navigation.Routes
import java.time.LocalDate

/** مسارات شاشات التقويم الفرعية (كلها تحت "calendar/..."). القيم البديلة غير فارغة دائمًا لسلامة الملاحة. */
object CalendarRoutes {
    const val DAY_PATTERN = "calendar/day?date={date}"
    fun day(date: LocalDate) = "calendar/day?date=$date"

    const val EVENT_PATTERN = "calendar/event?id={id}&date={date}"
    fun event(id: String = "new", date: LocalDate) = "calendar/event?id=$id&date=$date"

    const val EVENTS = "calendar/events"
}

/** رسم التقويم: الشبكة الشهرية، تفاصيل اليوم، محرر الحدث الشخصي، قائمة المناسبات. */
fun NavGraphBuilder.calendarGraph(nav: NavHostController, c: AppContainer) {
    navigation(startDestination = Routes.CALENDAR_HOME, route = Routes.CALENDAR) {
        composable(Routes.CALENDAR_HOME) { CalendarHomeScreen(nav) }
        composable(
            CalendarRoutes.DAY_PATTERN,
            arguments = listOf(navArgument("date") { type = NavType.StringType; defaultValue = "today" }),
        ) { entry ->
            DayDetailScreen(nav, ArabicFormat.parseDate(entry.arguments?.getString("date")) ?: c.clock.today())
        }
        composable(
            CalendarRoutes.EVENT_PATTERN,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType; defaultValue = "new" },
                navArgument("date") { type = NavType.StringType; defaultValue = "today" },
            ),
        ) { entry ->
            EventEditorScreen(
                nav,
                id = entry.arguments?.getString("id") ?: "new",
                date = ArabicFormat.parseDate(entry.arguments?.getString("date")) ?: c.clock.today(),
            )
        }
        composable(CalendarRoutes.EVENTS) { EventsListScreen(nav) }
    }
}
