package com.digitalclockpro.presentation.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.presentation.common.AlarmPermissionBanner
import com.digitalclockpro.presentation.common.rememberCurrentTime
import java.time.Instant
import java.time.ZoneId
import androidx.compose.ui.res.stringResource
import com.digitalclockpro.R

/**
 * Main dashboard: a large animated clock on top, the next-alarm card in the middle and quick
 * entries to the Widget Studio / World clock at the bottom.
 */
@Composable
fun DashboardScreen(
    onOpenAlarms: () -> Unit,
    onOpenWorldClock: () -> Unit,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val now by rememberCurrentTime(withSeconds = state.preferences.showSecondsInApp)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AlarmPermissionBanner()
        Spacer(Modifier.height(16.dp))

        // ---- hero clock ----
        val secondsAlpha by animateFloatAsState(
            targetValue = if (now.second % 2 == 0) 1f else 0.35f,
            label = "secondsBlink"
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = TimeFormatters.formatTime(
                    now,
                    state.preferences.use24Hour,
                    showSeconds = false
                ),
                fontSize = 76.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (state.preferences.showSecondsInApp) {
                Text(
                    text = ":%02d".format(now.second),
                    fontSize = 32.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.alpha(secondsAlpha).padding(bottom = 12.dp)
                )
            }
            if (!state.preferences.use24Hour) {
                Text(
                    text = TimeFormatters.amPm(now),
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(start = 6.dp, bottom = 16.dp)
                )
            }
        }
        Text(
            text = TimeFormatters.formatDate(now, stringResource(R.string.dashboard_date_pattern)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(28.dp))

        // ---- next alarm ----
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Alarm, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Column(Modifier.padding(start = 14.dp)) {
                        val alarm = state.nextAlarm
                        if (alarm == null) {
                            Text(stringResource(R.string.dashboard_no_upcoming_alarm), style = MaterialTheme.typography.titleMedium)
                            Text(
                                stringResource(R.string.dashboard_tap_to_create),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            val trigger = alarm.nextTriggerAtMillis()
                            val at = Instant.ofEpochMilli(trigger).atZone(ZoneId.systemDefault())
                            Text(
                                TimeFormatters.formatTime(at, state.preferences.use24Hour, false) +
                                    if (state.preferences.use24Hour) "" else " ${TimeFormatters.amPm(at)}",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Text(
                                listOfNotNull(
                                    alarm.label.takeIf { it.isNotBlank() },
                                    TimeFormatters.countdown(System.currentTimeMillis(), trigger)
                                ).joinToString(" • "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                Switch(
                    checked = state.nextAlarm?.enabled == true,
                    onCheckedChange = viewModel::onToggleNextAlarm,
                    enabled = state.nextAlarm != null
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(onClick = onOpenAlarms, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Alarm, contentDescription = null)
                Text(stringResource(R.string.dashboard_alarms_count, state.enabledAlarmCount))
            }
            OutlinedButton(onClick = onOpenWorldClock, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Widgets, contentDescription = null)
                Text(stringResource(R.string.dashboard_world))
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.dashboard_widget_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        )
    }
}
