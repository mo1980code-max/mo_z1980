package com.digitalclockpro.presentation.alarms

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.data.ringtone.AlarmSound
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
    private val saveAlarm: SaveAlarmUseCase
) : ViewModel() {

    private val alarmId: Long = savedStateHandle[Routes.ALARM_EDIT_ARG] ?: 0L

    private val _alarm = MutableStateFlow(
        Alarm(hour = LocalTime.now().hour, minute = 0)
    )
    val alarm: StateFlow<Alarm> = _alarm.asStateFlow()

    private val _sounds = MutableStateFlow(SoundPickerState())
    val sounds: StateFlow<SoundPickerState> = _sounds.asStateFlow()

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

    fun selectSound(sound: AlarmSound) = setSound(sound.uri, sound.title)

    /**
     * Imports a user-picked audio file by **copying it into app storage**, so the alarm keeps
     * working after the Uri permission is revoked or the original file is moved/deleted.
     */
    fun importRingtone(uri: Uri) = viewModelScope.launch {
        _sounds.update { it.copy(importing = true, importError = null) }
        val imported = ringtoneRepository.importFromUri(uri)
        if (imported == null) {
            _sounds.update {
                it.copy(
                    importing = false,
                    importError = "Could not import this file. Pick an audio file " +
                        "(mp3, wav, ogg, m4a, flac) smaller than 25 MB."
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
                system = listOf(ringtoneRepository.defaultAlarmSound()) +
                    ringtoneRepository.systemAlarmSounds(),
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
        val importError: String? = null,
        val currentSoundMissing: Boolean = false
    )

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        saveAlarm(_alarm.value.copy(enabled = true, currentSnoozeCount = 0))
        onDone()
    }
}
