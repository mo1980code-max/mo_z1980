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
import androidx.compose.ui.res.stringResource
import com.digitalclockpro.R
import androidx.compose.ui.platform.LocalContext

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
            stringResource(R.string.settings_appearance),
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

        SwitchRow(stringResource(R.string.settings_dynamic_color), prefs.dynamicColor, viewModel::setDynamicColor)
        SwitchRow(stringResource(R.string.settings_amoled), prefs.amoledBlack, viewModel::setAmoled)
        SwitchRow(stringResource(R.string.settings_24h), prefs.use24Hour, viewModel::set24Hour)
        SwitchRow(stringResource(R.string.settings_show_seconds), prefs.showSecondsInApp, viewModel::setShowSeconds)
        SwitchRow(stringResource(R.string.settings_weather), prefs.weatherEnabled, viewModel::setWeatherEnabled)
        SwitchRow(stringResource(R.string.settings_celsius), prefs.weatherCelsius, viewModel::setCelsius)
        SwitchRow(stringResource(R.string.settings_keep_screen_on), prefs.keepScreenOnDashboard, viewModel::setKeepScreenOn)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text(
            stringResource(R.string.settings_reliability),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        ListItem(
            headlineContent = {
                Text(
                    stringResource(
                        R.string.oem_title,
                        OemPowerSettings.vendorLabel(LocalContext.current)
                    )
                )
            },
            supportingContent = {
                Text(
                    if (OemPowerSettings.requiresManualWhitelisting)
                        stringResource(R.string.settings_oem_warning, OemPowerSettings.romName)
                    else stringResource(R.string.settings_oem_subtitle)
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
