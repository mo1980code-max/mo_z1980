package com.digitalclockpro.presentation

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.ads.AdsController
import com.digitalclockpro.ads.AdPolicyBanner
import com.digitalclockpro.clockengine.AdSurface
import com.digitalclockpro.presentation.alarms.AlarmEditScreen
import com.digitalclockpro.presentation.alarms.AlarmListScreen
import com.digitalclockpro.presentation.dashboard.DashboardScreen
import com.digitalclockpro.presentation.navigation.Routes
import com.digitalclockpro.presentation.navigation.TopLevelDestination
import com.digitalclockpro.presentation.settings.SettingsScreen
import com.digitalclockpro.presentation.settings.oem.OemSetupScreen
import com.digitalclockpro.presentation.world.WorldClockScreen
import androidx.compose.ui.res.stringResource
import com.digitalclockpro.presentation.deskclock.FullScreenClockActivity
import com.digitalclockpro.presentation.timer.TimerTabScreen

@Composable
fun DigitalClockProApp(
    startDestination: TopLevelDestination,
    adsController: AdsController
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    // The composition is hosted by MainActivity; null only outside an activity (previews).
    val activity = LocalContext.current as? Activity
    val adsReady by adsController.adsReady.collectAsStateWithLifecycle()

    // The chess clock is a TAB inside the Timer destination, not a route of its own, so the
    // timer screen reports the active tab upwards and it is folded into the surface below.
    var timerTabSurface by remember { mutableStateOf(AdSurface.TIMER) }

    // Derived in the SAME composition as the banner: when the route flips to the alarm
    // editor or the OEM guide the banner disappears on that frame — there is no window
    // where an ad-free surface still shows the previous screen's banner.
    val surface = surfaceForRoute(currentRoute, timerTabSurface)

    // Keep the app-open-ad gate in sync with what the user is actually looking at.
    LaunchedEffect(surface, activity) {
        activity?.let { adsController.setSurface(it, surface) }
    }

    Scaffold(
        bottomBar = {
            Column {
                // The single banner of the app. AdPolicy decides everything: on the six
                // ad-free surfaces (ringing, desk clock, chess clock, alarm editor, OEM
                // guide, widgets) this renders nothing at all.
                AdPolicyBanner(
                    surface = surface,
                    adsReady = adsReady,
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentRoute == null || TopLevelDestination.entries.any { it.route == currentRoute }) {
                    NavigationBar {
                        TopLevelDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = currentRoute == destination.route,
                                onClick = {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        destination.icon,
                                        contentDescription = stringResource(destination.labelRes)
                                    )
                                },
                                label = { Text(stringResource(destination.labelRes)) }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                val context = LocalContext.current
                DashboardScreen(
                    onOpenAlarms = { navController.navigate(TopLevelDestination.ALARMS.route) },
                    onOpenWorldClock = { navController.navigate(TopLevelDestination.WORLD.route) },
                    // Separate activity, not a nav destination: the desk clock owns the window
                    // (immersive bars, keep-screen-on, brightness override).
                    onOpenDeskClock = { context.startActivity(FullScreenClockActivity.intent(context)) }
                )
            }
            composable(TopLevelDestination.WORLD.route) { WorldClockScreen() }
            composable(TopLevelDestination.ALARMS.route) {
                AlarmListScreen(
                    onAddAlarm = { navController.navigate(Routes.alarmEdit()) },
                    onEditAlarm = { id -> navController.navigate(Routes.alarmEdit(id)) }
                )
            }
            composable(TopLevelDestination.TIMER.route) {
                TimerTabScreen(onSurfaceChanged = { timerTabSurface = it })
            }
            composable(TopLevelDestination.SETTINGS.route) {
                SettingsScreen(
                    onOpenOemGuide = { navController.navigate(Routes.OEM_SETUP) }
                )
            }
            composable(Routes.OEM_SETUP) { OemSetupScreen() }
            composable(
                route = "${Routes.ALARM_EDIT}/{${Routes.ALARM_EDIT_ARG}}",
                arguments = listOf(navArgument(Routes.ALARM_EDIT_ARG) { type = NavType.LongType })
            ) {
                AlarmEditScreen(onDone = { navController.popBackStack() })
            }
        }
    }
}

/**
 * Maps the navigation state onto the pure [AdSurface] consumed by AdPolicy. This is plain
 * translation, not decision-making: eligibility is decided only in
 * [com.digitalclockpro.clockengine.AdPolicy]. The Timer route defers to the active tab
 * (timer / stopwatch / chess clock).
 */
private fun surfaceForRoute(route: String?, timerTabSurface: AdSurface): AdSurface = when {
    route == TopLevelDestination.TIMER.route -> timerTabSurface
    route == TopLevelDestination.WORLD.route -> AdSurface.WORLD_CLOCK
    route == TopLevelDestination.ALARMS.route -> AdSurface.ALARM_LIST
    route == TopLevelDestination.SETTINGS.route -> AdSurface.SETTINGS
    route == Routes.OEM_SETUP -> AdSurface.OEM_GUIDE
    // Matches both the "alarm_edit/{alarmId}" pattern and any concrete value.
    route != null && route.startsWith("${Routes.ALARM_EDIT}/") -> AdSurface.ALARM_EDITOR
    else -> AdSurface.DASHBOARD
}
