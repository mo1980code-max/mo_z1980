package com.digitalclockpro.presentation.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.ui.graphics.vector.ImageVector
import com.digitalclockpro.R

enum class TopLevelDestination(
    val route: String,
    /** Localized label; resolved with `stringResource` at the call site. */
    @StringRes val labelRes: Int,
    val icon: ImageVector
) {
    DASHBOARD("dashboard", R.string.nav_clock, Icons.Filled.WatchLater),
    WORLD("world", R.string.nav_world, Icons.Filled.Public),
    ALARMS("alarms", R.string.nav_alarms, Icons.Filled.Alarm),
    SETTINGS("settings", R.string.nav_settings, Icons.Filled.Settings);

    companion object {
        fun fromRoute(route: String?): TopLevelDestination =
            entries.firstOrNull { it.route == route } ?: DASHBOARD
    }
}

object Routes {
    const val OEM_SETUP = "oem_setup"
    const val ALARM_EDIT = "alarm_edit"
    const val ALARM_EDIT_ARG = "alarmId"
    fun alarmEdit(alarmId: Long = 0L) = "$ALARM_EDIT/$alarmId"
}
