package com.digitalclockpro.presentation.alarms

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.domain.model.DismissChallenge
import com.digitalclockpro.domain.model.VibrationPattern
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    onDone: () -> Unit,
    viewModel: AlarmEditViewModel = hiltViewModel()
) {
    val alarm by viewModel.alarm.collectAsStateWithLifecycle()
    val timeState = rememberTimePickerState(
        initialHour = alarm.hour,
        initialMinute = alarm.minute,
        is24Hour = false
    )

    LaunchedEffect(timeState.hour, timeState.minute) {
        viewModel.setTime(timeState.hour, timeState.minute)
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TimePicker(state = timeState, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(
            value = alarm.label,
            onValueChange = viewModel::setLabel,
            label = { Text("Label") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Section("Repeat") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in alarm.repeatDays,
                        onClick = { viewModel.toggleDay(day) },
                        label = { Text(day.getDisplayName(TextStyle.SHORT, Locale.getDefault())) }
                    )
                }
            }
        }

        Section("Volume ${alarm.volumePercent}%") {
            Slider(
                value = alarm.volumePercent.toFloat(),
                onValueChange = { viewModel.setVolume(it.toInt()) },
                valueRange = 0f..100f
            )
        }

        Section("Volume ramp: ${alarm.volumeRampSeconds}s") {
            Slider(
                value = alarm.volumeRampSeconds.toFloat(),
                onValueChange = { viewModel.setRamp(it.toInt()) },
                valueRange = 0f..60f,
                steps = 11
            )
        }

        Section("Snooze: ${alarm.snoozeMinutes} min, max ${alarm.maxSnoozeCount}×") {
            Slider(
                value = alarm.snoozeMinutes.toFloat(),
                onValueChange = { viewModel.setSnooze(it.toInt()) },
                valueRange = 1f..30f,
                steps = 28
            )
            Slider(
                value = alarm.maxSnoozeCount.toFloat(),
                onValueChange = { viewModel.setMaxSnooze(it.toInt()) },
                valueRange = 0f..10f,
                steps = 9
            )
        }

        Section("Vibration") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                VibrationPattern.entries.forEach { pattern ->
                    FilterChip(
                        selected = alarm.vibrationPattern == pattern,
                        onClick = { viewModel.setVibration(pattern) },
                        label = { Text(pattern.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
        }

        Section("Dismiss challenge") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val options = listOf(
                    "None" to DismissChallenge.None,
                    "Math" to DismissChallenge.Math(),
                    "Shake" to DismissChallenge.Shake(),
                    "Memory" to DismissChallenge.Sequence()
                )
                options.forEach { (label, challenge) ->
                    FilterChip(
                        selected = alarm.challenge::class == challenge::class,
                        onClick = { viewModel.setChallenge(challenge) },
                        label = { Text(label) }
                    )
                }
            }
            when (val c = alarm.challenge) {
                is DismissChallenge.Math -> {
                    Text("Problems: ${c.problemCount} • ${c.difficulty.name.lowercase()}")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DismissChallenge.Difficulty.entries.forEach { difficulty ->
                            FilterChip(
                                selected = c.difficulty == difficulty,
                                onClick = { viewModel.setChallenge(c.copy(difficulty = difficulty)) },
                                label = { Text(difficulty.name.lowercase()) }
                            )
                        }
                    }
                    Slider(
                        value = c.problemCount.toFloat(),
                        onValueChange = { viewModel.setChallenge(c.copy(problemCount = it.toInt())) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                }
                is DismissChallenge.Shake -> {
                    Text("Shakes required: ${c.shakeCount}")
                    Slider(
                        value = c.shakeCount.toFloat(),
                        onValueChange = { viewModel.setChallenge(c.copy(shakeCount = it.toInt())) },
                        valueRange = 10f..50f,
                        steps = 7
                    )
                }
                else -> Unit
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { viewModel.save(onDone) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Save alarm") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
