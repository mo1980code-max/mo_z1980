package com.digitalclockpro.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(val route: String, val label: String, val icon: ImageVector) {
    DASHBOARD("dashboard", "Clock", Icons.Filled.WatchLater),
    WORLD("world", "World", Icons.Filled.Public),
    ALARMS("alarms", "Alarms", Icons.Filled.Alarm),
    SETTINGS("settings", "Settings", Icons.Filled.Settings);

    companion object {
        fun fromRoute(route: String?): TopLevelDestination =
            entries.firstOrNull { it.route == route } ?: DASHBOARD
    }
}

object Routes {
    const val ALARM_EDIT = "alarm_edit"
    const val ALARM_EDIT_ARG = "alarmId"
    fun alarmEdit(alarmId: Long = 0L) = "$ALARM_EDIT/$alarmId"
}
