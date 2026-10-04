package com.digitalclockpro.presentation.alarms

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val saveAlarm: SaveAlarmUseCase
) : ViewModel() {

    private val alarmId: Long = savedStateHandle[Routes.ALARM_EDIT_ARG] ?: 0L

    private val _alarm = MutableStateFlow(
        Alarm(hour = LocalTime.now().hour, minute = 0)
    )
    val alarm: StateFlow<Alarm> = _alarm.asStateFlow()

    init {
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
    fun setSound(uri: String?, title: String) = _alarm.update { it.copy(soundUri = uri, soundTitle = title) }
    fun setChallenge(challenge: DismissChallenge) = _alarm.update { it.copy(challenge = challenge) }

    fun save(onDone: () -> Unit) = viewModelScope.launch {
        saveAlarm(_alarm.value.copy(enabled = true, currentSnoozeCount = 0))
        onDone()
    }
}
