package com.digitalclockpro.presentation.alarms

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.model.DismissChallenge
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.ui.res.stringResource
import com.digitalclockpro.R

@Composable
fun AlarmListScreen(
    onAddAlarm: () -> Unit,
    onEditAlarm: (Long) -> Unit,
    viewModel: AlarmListViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddAlarm) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.add_alarm))
            }
        }
    ) { padding ->
        if (state.alarms.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.no_alarms), style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.no_alarms_hint),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.alarms, key = { it.id }) { alarm ->
                AlarmRow(
                    alarm = alarm,
                    use24Hour = state.use24Hour,
                    onClick = { onEditAlarm(alarm.id) },
                    onToggle = { viewModel.onToggle(alarm.id, it) },
                    onDelete = { viewModel.onDelete(alarm.id) }
                )
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    use24Hour: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = TimeFormatters.formatTime(
                        java.time.LocalDateTime.of(
                            java.time.LocalDate.now(), alarm.time
                        ),
                        use24Hour, showSeconds = false
                    ),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 34.sp,
                    color = if (alarm.enabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    // Hoisted out of buildString: @Composable calls cannot run inside a lambda
                    // that is not itself composable.
                    text = listOfNotNull(
                        repeatLabel(alarm),
                        alarm.label.takeIf { it.isNotBlank() },
                        challengeLabel(alarm.challenge)
                    ).joinToString(stringResource(R.string.bullet_separator)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (alarm.enabled) {
                    Text(
                        TimeFormatters.countdown(
                            System.currentTimeMillis(), alarm.nextTriggerAtMillis()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
            Switch(checked = alarm.enabled, onCheckedChange = onToggle)
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_alarm))
            }
        }
    }
}

/**
 * Composable so the labels follow the app locale; day names come from [DayOfWeek.getDisplayName]
 * with the default locale, which is already localized (and RTL-ordered) by the JDK.
 */
@Composable
private fun repeatLabel(alarm: Alarm): String = when {
    alarm.repeatDays.isEmpty() -> stringResource(R.string.repeat_once)
    alarm.repeatDays.size == 7 -> stringResource(R.string.repeat_every_day)
    alarm.repeatDays == setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    ) -> stringResource(R.string.repeat_weekdays)
    alarm.repeatDays == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) ->
        stringResource(R.string.repeat_weekends)
    else -> DayOfWeek.entries.filter { it in alarm.repeatDays }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

@Composable
private fun challengeLabel(challenge: DismissChallenge): String? = when (challenge) {
    is DismissChallenge.Math ->
        stringResource(R.string.challenge_math_short, challenge.problemCount)
    is DismissChallenge.Shake ->
        stringResource(R.string.challenge_shake_short, challenge.shakeCount)
    is DismissChallenge.Sequence -> stringResource(R.string.challenge_memory_short)
    DismissChallenge.None -> null
}
