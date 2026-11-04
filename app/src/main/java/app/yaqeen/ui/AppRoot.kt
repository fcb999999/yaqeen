package app.yaqeen.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import app.yaqeen.ui.components.YaqeenTile
import app.yaqeen.ui.components.YaqeenTopBar
import app.yaqeen.ui.navigation.Routes
import app.yaqeen.ui.theme.Area
import app.yaqeen.ui.theme.ThemeStyle
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
    val themeStyle by container.settings.observe(Settings.THEME_STYLE).collectAsState(initial = ThemeStyle.CLASSIC)
    CompositionLocalProvider(LocalAppContainer provides container) {
        YaqeenTheme(themeMode, themeStyle) {
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

            // حياة: تدرّج الترويسة يُرسم في خلفية الشاشة كلها فيشمل منطقة شريط الحالة (لا يظهر حزام بلون آخر)
            val bg = MaterialTheme.colorScheme.background
            val headerTint = MaterialTheme.colorScheme.primaryContainer
            val headerFadePx = with(LocalDensity.current) { 260.dp.toPx() }
            val rootModifier = if (YaqeenTheme.isHayat) {
                Modifier.background(
                    Brush.verticalGradient(
                        listOf(headerTint.copy(alpha = if (YaqeenTheme.extras.dark) 0.30f else 0.85f), bg),
                        startY = 0f,
                        endY = headerFadePx,
                    ),
                )
            } else Modifier
            Scaffold(
                modifier = rootModifier,
                containerColor = if (YaqeenTheme.isHayat) Color.Transparent else bg,
                bottomBar = {
                    if (showBar) {
                        val select: (Tab) -> Unit = { tab ->
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                        val isSelected: (Tab) -> Boolean = { tab ->
                            backStack?.destination?.hierarchy?.any { it.route == tab.route } == true
                        }
                        val ex = YaqeenTheme.extras
                        if (ex.floatingNav) {
                            // حياة: شريط عائم على شكل حبة (Scaffold يحجز ارتفاعه فلا يغطي المحتوى).
                            HayatNavBar(TABS, isSelected, select)
                        } else {
                            // كلاسيكي ونور: شريط مسطّح؛ في «نور» العنصر النشط أزرق بلا خلفية كما في الصور
                            NavigationBar(containerColor = if (ex.isNoor) ex.navContainer else MaterialTheme.colorScheme.surface) {
                                TABS.forEach { tab ->
                                    NavigationBarItem(
                                        selected = isSelected(tab),
                                        onClick = { select(tab) },
                                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                                        label = { Text(tab.label) },
                                        colors = if (ex.isNoor) {
                                            androidx.compose.material3.NavigationBarItemDefaults.colors(
                                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                                indicatorColor = Color.Transparent,
                                                unselectedIconColor = ex.onNav,
                                                unselectedTextColor = ex.onNav,
                                            )
                                        } else {
                                            androidx.compose.material3.NavigationBarItemDefaults.colors()
                                        },
                                    )
                                }
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
    val entries = listOf(
        MoreEntry("المهام اليومية", Routes.TASKS, "مهامك وتقدمك اليومي", Icons.Filled.Checklist, Area.TASKS),
        MoreEntry("التقويم", Routes.CALENDAR, "الهجري والميلادي والمناسبات", Icons.Filled.CalendarMonth, Area.CALENDAR),
        MoreEntry("رحلتي مع يقين", Routes.JOURNEY, "إحصائياتك الشخصية", Icons.Filled.Timeline, Area.JOURNEY),
        MoreEntry("الإعدادات", Routes.SETTINGS, "الحساب والإشعارات والمظهر", Icons.Filled.Settings, Area.SETTINGS),
    )
    Column(Modifier.fillMaxSize()) {
        YaqeenTopBar("المزيد")
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            if (YaqeenTheme.isHayat) {
                // حياة: بلاطات ملوّنة، لكل مجال لونه.
                Spacer(Modifier.height(4.dp))
                entries.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { e ->
                            YaqeenTile(
                                label = e.title,
                                icon = e.icon,
                                onClick = { nav.navigate(e.route) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                hue = e.area.hue,
                                caption = e.subtitle,
                                labelStyle = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                YaqeenCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                    entries.forEach { e -> NavRow(e.title, { nav.navigate(e.route) }, e.subtitle, e.icon) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private class MoreEntry(val title: String, val route: String, val subtitle: String, val icon: ImageVector, val area: Area)

/** شريط التنقل العائم لنمط «حياة»: حبة بنفسجية بأيقونات بيضاء، والعنصر المحدد داخل دائرة بيضاء. */
@Composable
private fun HayatNavBar(tabs: List<Tab>, isSelected: (Tab) -> Boolean, onSelect: (Tab) -> Unit) {
    val x = YaqeenTheme.extras
    val shape = RoundedCornerShape(36.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(14.dp, shape, ambientColor = x.navContainer.copy(alpha = 0.40f), spotColor = x.navContainer.copy(alpha = 0.50f))
                .background(x.navContainer, shape)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val selected = isSelected(tab)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(tab) })
                        .padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(38.dp).background(if (selected) x.navSelected else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(tab.icon, contentDescription = null, tint = if (selected) x.onNavSelected else x.onNav, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        tab.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = x.onNav,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
