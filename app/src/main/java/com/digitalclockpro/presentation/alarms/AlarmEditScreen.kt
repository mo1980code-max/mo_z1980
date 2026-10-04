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
import androidx.compose.ui.res.stringResource
import com.digitalclockpro.R
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.runtime.DisposableEffect

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AlarmEditScreen(
    onDone: () -> Unit,
    viewModel: AlarmEditViewModel = hiltViewModel()
) {
    val alarm by viewModel.alarm.collectAsStateWithLifecycle()
    val sounds by viewModel.sounds.collectAsStateWithLifecycle()
    val previewingUri by viewModel.previewingUri.collectAsStateWithLifecycle()

    // Leaving the editor (back press, navigation, process teardown) always silences the preview.
    DisposableEffect(Unit) { onDispose { viewModel.stopPreview() } }
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
            label = { Text(stringResource(R.string.alarm_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Section(stringResource(R.string.alarm_repeat)) {
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

        Section(stringResource(R.string.alarm_volume, alarm.volumePercent)) {
            Slider(
                value = alarm.volumePercent.toFloat(),
                onValueChange = { viewModel.setVolume(it.toInt()) },
                valueRange = 0f..100f
            )
        }

        Section(stringResource(R.string.alarm_volume_ramp, alarm.volumeRampSeconds)) {
            Slider(
                value = alarm.volumeRampSeconds.toFloat(),
                onValueChange = { viewModel.setRamp(it.toInt()) },
                valueRange = 0f..60f,
                steps = 11
            )
        }

        Section(
            stringResource(
                R.string.alarm_snooze_summary,
                alarm.snoozeMinutes,
                alarm.maxSnoozeCount
            )
        ) {
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
            previewingUri = previewingUri,
            onSelect = viewModel::selectSound,
            onPreview = viewModel::previewSound,
            onDismissPreviewIssue = viewModel::dismissPreviewIssue,
            onImport = viewModel::importRingtone,
            onDeleteImported = viewModel::deleteImported,
            onDismissError = viewModel::dismissImportError
        )

        Section(stringResource(R.string.alarm_vibration)) {
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

        Section(stringResource(R.string.alarm_dismiss_challenge)) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val options = listOf(
                    stringResource(R.string.challenge_none) to DismissChallenge.None,
                    stringResource(R.string.challenge_math) to DismissChallenge.Math(),
                    stringResource(R.string.challenge_shake) to DismissChallenge.Shake(),
                    stringResource(R.string.challenge_memory) to DismissChallenge.Sequence()
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
                    Text(
                        stringResource(
                            R.string.challenge_problems,
                            c.problemCount,
                            difficultyLabel(c.difficulty)
                        )
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DismissChallenge.Difficulty.entries.forEach { difficulty ->
                            FilterChip(
                                selected = c.difficulty == difficulty,
                                onClick = { viewModel.setChallenge(c.copy(difficulty = difficulty)) },
                                label = { Text(difficultyLabel(difficulty)) }
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
                    Text(stringResource(R.string.challenge_shakes_required, c.shakeCount))
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
        ) { Text(stringResource(R.string.save_alarm)) }
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
@Composable
private fun difficultyLabel(difficulty: DismissChallenge.Difficulty): String = when (difficulty) {
    DismissChallenge.Difficulty.EASY -> stringResource(R.string.difficulty_easy)
    DismissChallenge.Difficulty.MEDIUM -> stringResource(R.string.difficulty_medium)
    DismissChallenge.Difficulty.HARD -> stringResource(R.string.difficulty_hard)
}

@Composable
private fun SoundSection(
    currentUri: String?,
    currentTitle: String,
    state: AlarmEditViewModel.SoundPickerState,
    previewingUri: String?,
    onSelect: (AlarmSound) -> Unit,
    onPreview: (AlarmSound) -> Unit,
    onDismissPreviewIssue: () -> Unit,
    onImport: (android.net.Uri) -> Unit,
    onDeleteImported: (AlarmSound) -> Unit,
    onDismissError: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(onImport) }

    Section(stringResource(R.string.alarm_sound)) {
        Text(
            text = currentTitle.ifBlank {
                if (currentUri == AlarmSound.SILENT_URI) stringResource(R.string.sound_silent)
                else stringResource(R.string.sound_default)
            },
            style = MaterialTheme.typography.bodyMedium
        )
        if (state.currentSoundMissing) {
            Text(
                stringResource(R.string.sound_missing_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        state.previewIssue?.let { issue ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when (issue) {
                        AlarmEditViewModel.PreviewIssue.ALARM_RINGING ->
                            stringResource(R.string.sound_preview_blocked_ringing)
                        AlarmEditViewModel.PreviewIssue.FAILED ->
                            stringResource(R.string.sound_preview_failed)
                    },
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onDismissPreviewIssue) { Text(stringResource(R.string.ok)) }
            }
        }
        state.importError?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.sound_import_failed),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(onClick = onDismissError) { Text(stringResource(R.string.ok)) }
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
                Text("  " + stringResource(R.string.sound_pick_from_device))
            }
            if (state.importing) {
                CircularProgressIndicator(modifier = Modifier.padding(start = 12.dp))
            }
        }

        SoundRow(
            title = stringResource(R.string.sound_silent),
            selected = currentUri == AlarmSound.SILENT_URI,
            onClick = { onSelect(AlarmSound.silent()) }
        )

        if (state.imported.isNotEmpty()) {
            Text(stringResource(R.string.sound_imported_header), style = MaterialTheme.typography.labelSmall)
            state.imported.forEach { sound ->
                SoundRow(
                    title = sound.title,
                    selected = sound.uri == currentUri,
                    onClick = { onSelect(sound) },
                    playing = sound.uri != null && sound.uri == previewingUri,
                    onPreview = { onPreview(sound) },
                    trailing = {
                        IconButton(onClick = { onDeleteImported(sound) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription =
                                    stringResource(R.string.sound_delete_imported)
                            )
                        }
                    }
                )
            }
        }

        if (state.system.isNotEmpty()) {
            Text(stringResource(R.string.sound_device_header), style = MaterialTheme.typography.labelSmall)
            state.system.take(MAX_SYSTEM_SOUNDS).forEach { sound ->
                SoundRow(
                    title = sound.title,
                    selected = sound.uri == currentUri,
                    onClick = { onSelect(sound) },
                    playing = sound.uri != null && sound.uri == previewingUri,
                    onPreview = { onPreview(sound) }
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
    /** True while this row is being auditioned; turns the button into a stop control. */
    playing: Boolean = false,
    onPreview: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(title, modifier = Modifier.weight(1f))
        if (onPreview != null) {
            IconButton(onClick = onPreview) {
                Icon(
                    imageVector = if (playing) Icons.Filled.StopCircle
                    else Icons.Filled.PlayCircleOutline,
                    contentDescription = stringResource(
                        if (playing) R.string.sound_preview_stop else R.string.sound_preview
                    ),
                    tint = if (playing) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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
