package com.digitalclockpro.presentation.alarms

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.data.ringtone.AlarmSound
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
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
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

        SoundSection(
            currentUri = alarm.soundUri,
            currentTitle = alarm.soundTitle,
            state = sounds,
            onSelect = viewModel::selectSound,
            onImport = viewModel::importRingtone,
            onDeleteImported = viewModel::deleteImported,
            onDismissError = viewModel::dismissImportError
        )

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

/**
 * Alarm sound picker: silent, system alarm tones, and user-imported audio files.
 *
 * Imported files are copied into `filesDir/ringtones/` by the repository, so the alarm keeps
 * playing even if the source file is deleted or the Uri grant is revoked. `OpenDocument` is used
 * (rather than `GetContent`) because it returns a stable, re-openable document Uri.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SoundSection(
    currentUri: String?,
    currentTitle: String,
    state: AlarmEditViewModel.SoundPickerState,
    onSelect: (AlarmSound) -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onDeleteImported: (AlarmSound) -> Unit,
    onDismissError: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImport) }

    Section("Alarm sound") {
        Text(
            text = currentTitle.ifBlank { if (currentUri == null) "Silent" else "Default alarm sound" },
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.currentSoundMissing) {
            Text(
                "This sound is no longer available — pick another one or the default alarm " +
                    "tone will be used.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        state.importError?.let { error ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                OutlinedButton(onClick = onDismissError) { Text("OK") }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(
                onClick = {
                    picker.launch(
                        arrayOf(
                            "audio/*",
                            "application/ogg",
                            "application/x-ogg"
                        )
                    )
                },
                enabled = !state.importing
            ) {
                Icon(Icons.Filled.LibraryMusic, contentDescription = null)
                Text("  Pick from device")
            }
            if (state.importing) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp))
            }
        }

        SoundRow(
            title = "Silent",
            selected = currentUri == null,
            onClick = { onSelect(AlarmSound(null, "Silent", AlarmSound.Source.SILENT)) }
        )

        if (state.imported.isNotEmpty()) {
            Text("Imported", style = MaterialTheme.typography.labelSmall)
            state.imported.forEach { sound ->
                SoundRow(
                    title = sound.title,
                    selected = sound.uri == currentUri,
                    onClick = { onSelect(sound) },
                    trailing = {
                        IconButton(onClick = { onDeleteImported(sound) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete imported sound")
                        }
                    }
                )
            }
        }

        if (state.system.isNotEmpty()) {
            Text("Device ringtones", style = MaterialTheme.typography.labelSmall)
            state.system.take(MAX_SYSTEM_SOUNDS).forEach { sound ->
                SoundRow(
                    title = sound.title,
                    selected = sound.uri == currentUri,
                    onClick = { onSelect(sound) }
                )
            }
        }
    }
}

private const val MAX_SYSTEM_SOUNDS = 30

@Composable
private fun SoundRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
