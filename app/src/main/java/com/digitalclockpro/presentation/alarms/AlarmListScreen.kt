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
                Icon(Icons.Filled.Add, contentDescription = "Add alarm")
            }
        }
    ) { padding ->
        if (state.alarms.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No alarms yet", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Tap + to create your first alarm.",
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
                    text = buildString {
                        append(repeatLabel(alarm))
                        if (alarm.label.isNotBlank()) append(" • ${alarm.label}")
                        challengeLabel(alarm.challenge)?.let { append(" • $it") }
                    },
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
                Icon(Icons.Filled.Delete, contentDescription = "Delete alarm")
            }
        }
    }
}

private fun repeatLabel(alarm: Alarm): String = when {
    alarm.repeatDays.isEmpty() -> "Once"
    alarm.repeatDays.size == 7 -> "Every day"
    alarm.repeatDays == setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
    ) -> "Weekdays"
    alarm.repeatDays == setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekends"
    else -> DayOfWeek.entries.filter { it in alarm.repeatDays }
        .joinToString(" ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
}

private fun challengeLabel(challenge: DismissChallenge): String? = when (challenge) {
    is DismissChallenge.Math -> "Math ×${challenge.problemCount}"
    is DismissChallenge.Shake -> "Shake ×${challenge.shakeCount}"
    is DismissChallenge.Sequence -> "Memory"
    DismissChallenge.None -> null
}
