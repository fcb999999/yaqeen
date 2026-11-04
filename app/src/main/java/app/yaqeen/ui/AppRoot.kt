package app.yaqeen.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.yaqeen.AppContainer
import app.yaqeen.data.prefs.LocalPrefs
import app.yaqeen.data.settings.Settings
import app.yaqeen.feature.adhkar.adhkarGraph
import app.yaqeen.feature.calendar.calendarGraph
import app.yaqeen.feature.home.HomeScreen
import app.yaqeen.feature.journey.journeyGraph
import app.yaqeen.feature.prayer.prayerGraph
import app.yaqeen.feature.quran.quranGraph
import app.yaqeen.feature.settings.settingsGraph
import app.yaqeen.feature.tasks.tasksGraph
import app.yaqeen.ui.components.NavRow
import app.yaqeen.ui.components.YaqeenCard
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.YaqeenTheme

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer غير متاح") }

/** ينشئ ViewModel يستقبل AppContainer. مثال: val vm = containerViewModel { MyViewModel(it) } */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalAppContainer.current
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}

private data class Tab(val route: String, val root: String, val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab(Routes.HOME, Routes.HOME, "الرئيسية", Icons.Filled.Home),
    Tab(Routes.QURAN, Routes.QURAN_HOME, "القرآن", Icons.Filled.MenuBook),
    Tab(Routes.ADHKAR, Routes.ADHKAR_HOME, "الأذكار", Icons.Filled.WbSunny),
    Tab(Routes.PRAYER, Routes.PRAYER_HOME, "الصلاة", Icons.Filled.AutoStories),
    Tab(Routes.MORE, Routes.MORE, "المزيد", Icons.Filled.MoreHoriz),
)

@Composable
fun AppRoot(container: AppContainer, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val themeMode by container.settings.observe(Settings.THEME_MODE).collectAsState(initial = "system")
    CompositionLocalProvider(LocalAppContainer provides container) {
        YaqeenTheme(themeMode) {
          app.yaqeen.feature.settings.ProvideUiFontScale(container) {
            val nav = rememberNavController()
            val backStack by nav.currentBackStackEntryAsState()
            val currentRoute = backStack?.destination?.route
            val showBar = currentRoute in Routes.TAB_ROOTS

            // أول تشغيل: صفحة الترحيب (تُنفَّذ ضمن ميزة الإعدادات)
            LaunchedEffect(Unit) {
                if (!container.localPrefs.getBool(LocalPrefs.Keys.ONBOARDING_DONE)) nav.navigate(Routes.ONBOARDING)
            }
            // فتح مسار قادم من إشعار
            LaunchedEffect(pendingRoute) {
                if (pendingRoute != null) {
                    runCatching { nav.navigate(pendingRoute) }
                    onRouteConsumed()
                }
            }

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                bottomBar = {
                    if (showBar) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            TABS.forEach { tab ->
                                val selected = backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        nav.navigate(tab.route) {
                                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(tab.icon, contentDescription = tab.label) },
                                    label = { Text(tab.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = nav,
                    startDestination = Routes.HOME,
                    modifier = Modifier.padding(padding),
                ) {
                    composable(Routes.HOME) { HomeScreen(nav, container) }
                    quranGraph(nav, container)
                    adhkarGraph(nav, container)
                    prayerGraph(nav, container)
                    composable(Routes.MORE) { MoreScreen(nav) }
                    tasksGraph(nav, container)
                    calendarGraph(nav, container)
                    journeyGraph(nav, container)
                    settingsGraph(nav, container)
                }
            }
          }
        }
    }
}

/** تبويب "المزيد": بوابة إلى المهام والتقويم والإحصائيات والإعدادات. */
@Composable
private fun MoreScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("المزيد")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            YaqeenCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                NavRow("المهام اليومية", { nav.navigate(Routes.TASKS) }, "مهامك وتقدمك اليومي", Icons.Filled.Checklist)
                NavRow("التقويم", { nav.navigate(Routes.CALENDAR) }, "الهجري والميلادي والمناسبات", Icons.Filled.CalendarMonth)
                NavRow("رحلتي مع يقين", { nav.navigate(Routes.JOURNEY) }, "إحصائياتك الشخصية", Icons.Filled.Timeline)
                NavRow("الإعدادات", { nav.navigate(Routes.SETTINGS) }, "الحساب والإشعارات والمظهر", Icons.Filled.Settings)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
