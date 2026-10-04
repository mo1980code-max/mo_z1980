package com.digitalclockpro.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.digitalclockpro.presentation.alarms.AlarmEditScreen
import com.digitalclockpro.presentation.alarms.AlarmListScreen
import com.digitalclockpro.presentation.dashboard.DashboardScreen
import com.digitalclockpro.presentation.navigation.Routes
import com.digitalclockpro.presentation.navigation.TopLevelDestination
import com.digitalclockpro.presentation.settings.SettingsScreen
import com.digitalclockpro.presentation.settings.oem.OemSetupScreen
import com.digitalclockpro.presentation.world.WorldClockScreen
import androidx.compose.ui.res.stringResource

@Composable
fun DigitalClockProApp(startDestination: TopLevelDestination) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        bottomBar = {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(TopLevelDestination.DASHBOARD.route) {
                DashboardScreen(
                    onOpenAlarms = { navController.navigate(TopLevelDestination.ALARMS.route) },
                    onOpenWorldClock = { navController.navigate(TopLevelDestination.WORLD.route) }
                )
            }
            composable(TopLevelDestination.WORLD.route) { WorldClockScreen() }
            composable(TopLevelDestination.ALARMS.route) {
                AlarmListScreen(
                    onAddAlarm = { navController.navigate(Routes.alarmEdit()) },
                    onEditAlarm = { id -> navController.navigate(Routes.alarmEdit(id)) }
                )
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
