package com.digitalclockpro.presentation.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.core.util.OemPowerSettings
import com.digitalclockpro.domain.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenOemGuide: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val prefs by viewModel.preferences.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Appearance",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            ThemeMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = prefs.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, ThemeMode.entries.size)
                ) { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) }
            }
        }

        SwitchRow("Material You dynamic color", prefs.dynamicColor, viewModel::setDynamicColor)
        SwitchRow("AMOLED pure black", prefs.amoledBlack, viewModel::setAmoled)
        SwitchRow("24-hour format", prefs.use24Hour, viewModel::set24Hour)
        SwitchRow("Show seconds in app", prefs.showSecondsInApp, viewModel::setShowSeconds)
        SwitchRow("Weather on widgets", prefs.weatherEnabled, viewModel::setWeatherEnabled)
        SwitchRow("Use °C", prefs.weatherCelsius, viewModel::setCelsius)
        SwitchRow("Keep screen on (dashboard)", prefs.keepScreenOnDashboard, viewModel::setKeepScreenOn)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            "Reliability",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        ListItem(
            headlineContent = { Text("Make alarms reliable on ${OemPowerSettings.vendor.displayName}") },
            supportingContent = {
                Text(
                    if (OemPowerSettings.requiresManualWhitelisting)
                        "${OemPowerSettings.romName} can kill background alarms — action needed"
                    else "Battery optimization and autostart settings"
                )
            },
            leadingContent = { Icon(Icons.Filled.BatteryAlert, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenOemGuide)
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) }
    )
}
