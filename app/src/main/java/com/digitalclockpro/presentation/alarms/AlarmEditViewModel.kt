package com.digitalclockpro.presentation.alarms

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.data.ringtone.AlarmSound
import com.digitalclockpro.data.ringtone.RingtonePreviewPlayer
import com.digitalclockpro.data.ringtone.RingtoneRepository
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.model.DismissChallenge
import com.digitalclockpro.domain.model.VibrationPattern
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.repository.PreferencesRepository
import com.digitalclockpro.domain.usecase.SaveAlarmUseCase
import com.digitalclockpro.presentation.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime
import javax.inject.Inject

@HiltViewModel
class AlarmEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alarmRepository: AlarmRepository,
    private val preferencesRepository: PreferencesRepository,
    private val ringtoneRepository: RingtoneRepository,
    private val previewPlayer: RingtonePreviewPlayer,
    private val saveAlarm: SaveAlarmUseCase
) : ViewModel() {

    private val alarmId: Long = savedStateHandle[Routes.ALARM_EDIT_ARG] ?: 0L

    private val _alarm = MutableStateFlow(
        Alarm(hour = LocalTime.now().hour, minute = 0)
    )
    val alarm: StateFlow<Alarm> = _alarm.asStateFlow()

    private val _sounds = MutableStateFlow(SoundPickerState())
    val sounds: StateFlow<SoundPickerState> = _sounds.asStateFlow()

    /** Uri currently being auditioned in the picker (null = nothing playing). */
    val previewingUri: StateFlow<String?> = previewPlayer.previewingUri

    init {
        refreshSounds()
        viewModelScope.launch {
            if (alarmId > 0L) {
                alarmRepository.getAlarm(alarmId)?.let { _alarm.value = it }
            } else {
                val prefs = preferencesRepository.preferences.first()
                _alarm.update {
                    it.copy(
                        snoozeMinutes = prefs.defaultSnoozeMinutes,
                        volumeRampSeconds = prefs.defaultVolumeRampSeconds
                    )
                }
            }
        }
    }

    fun setTime(hour: Int, minute: Int) = _alarm.update { it.copy(hour = hour, minute = minute) }
    fun setLabel(label: String) = _alarm.update { it.copy(label = label) }
    fun toggleDay(day: DayOfWeek) = _alarm.update {
        it.copy(repeatDays = if (day in it.repeatDays) it.repeatDays - day else it.repeatDays + day)
    }
    fun setVolume(percent: Int) = _alarm.update { it.copy(volumePercent = percent.coerceIn(0, 100)) }
    fun setRamp(seconds: Int) = _alarm.update { it.copy(volumeRampSeconds = seconds.coerceIn(0, 60)) }
    fun setSnooze(minutes: Int) = _alarm.update { it.copy(snoozeMinutes = minutes.coerceIn(1, 30)) }
    fun setMaxSnooze(count: Int) = _alarm.update { it.copy(maxSnoozeCount = count.coerceIn(0, 10)) }
    fun setVibration(pattern: VibrationPattern) = _alarm.update { it.copy(vibrationPattern = pattern) }
    fun setSound(uri: String?, title: String) {
        _alarm.update { it.copy(soundUri = uri, soundTitle = title) }
        viewModelScope.launch { verifyCurrentSound() }
    }

    /** Selecting another sound always silences the running preview first. */
    fun selectSound(sound: AlarmSound) {
        previewPlayer.stop()
        setSound(sound.uri, sound.title)
    }

    /**
     * Auditions [sound] for a few seconds. Tapping the playing item again stops it.
     * Refuses to play while a real alarm is ringing and surfaces that as an inline message.
     */
    fun previewSound(sound: AlarmSound) {
        val message = when (previewPlayer.preview(sound.uri)) {
            RingtonePreviewPlayer.Result.AlarmRinging -> PreviewIssue.ALARM_RINGING
            is RingtonePreviewPlayer.Result.Failed -> PreviewIssue.FAILED
            else -> null
        }
        _sounds.update { it.copy(previewIssue = message) }
    }

    fun stopPreview() = previewPlayer.stop()

    fun dismissPreviewIssue() = _sounds.update { it.copy(previewIssue = null) }

    /** Why a preview could not be played; mapped to a string resource by the UI. */
    enum class PreviewIssue { ALARM_RINGING, FAILED }

    /** Why an import was rejected; mapped to a string resource by the UI. */
    enum class ImportIssue { FAILED }

    /**
     * Imports a user-picked audio file by **copying it into app storage**, so the alarm keeps
     * working after the Uri permission is revoked or the original file is moved/deleted.
     */
    fun importRingtone(uri: Uri) = viewModelScope.launch {
        _sounds.update { it.copy(importing = true, importError = null) }
        // The picker grants read access; the repository persists it only while copying.
        val imported = ringtoneRepository.importFromUri(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (imported == null) {
            _sounds.update {
                it.copy(
                    importing = false,
                    importError = ImportIssue.FAILED
                )
            }
            return@launch
        }
        setSound(imported.uri, imported.title)
        _sounds.update { it.copy(importing = false, importError = null) }
        refreshSounds()
    }

    fun deleteImported(sound: AlarmSound) = viewModelScope.launch {
        val uri = sound.uri ?: return@launch
        if (previewingUri.value == uri) previewPlayer.stop()
        if (ringtoneRepository.deleteImported(uri)) {
            if (_alarm.value.soundUri == uri) {
                val fallback = ringtoneRepository.defaultAlarmSound()
                setSound(fallback.uri, fallback.title)
            }
            refreshSounds()
        }
    }

    fun dismissImportError() = _sounds.update { it.copy(importError = null) }

    private fun refreshSounds() = viewModelScope.launch {
        _sounds.update {
            it.copy(
                system = listOf(
                    ringtoneRepository.defaultAlarmSound(),
                    ringtoneRepository.bundledFallbackSound()
                ) + ringtoneRepository.systemAlarmSounds(),
                imported = ringtoneRepository.importedSounds()
            )
        }
        verifyCurrentSound()
    }

    /** Flags a sound whose file disappeared so the user can pick another one before it fails. */
    private suspend fun verifyCurrentSound() {
        val playable = ringtoneRepository.isPlayable(_alarm.value.soundUri)
        _sounds.update { it.copy(currentSoundMissing = !playable) }
    }
    fun setChallenge(challenge: DismissChallenge) = _alarm.update { it.copy(challenge = challenge) }

    data class SoundPickerState(
        val system: List<AlarmSound> = emptyList(),
        val imported: List<AlarmSound> = emptyList(),
        val importing: Boolean = false,
        val importError: ImportIssue? = null,
        val currentSoundMissing: Boolean = false,
        val previewIssue: PreviewIssue? = null
    )

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        previewPlayer.stop()
        saveAlarm(_alarm.value.copy(enabled = true, currentSnoozeCount = 0))
        onDone()
    }

    /** Leaving the editor (back, process death, config change teardown) silences the preview. */
    override fun onCleared() {
        previewPlayer.stop()
        super.onCleared()
    }
}
