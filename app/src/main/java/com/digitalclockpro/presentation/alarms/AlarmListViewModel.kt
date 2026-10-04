package com.digitalclockpro.presentation.alarms

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.repository.PreferencesRepository
import com.digitalclockpro.domain.usecase.DeleteAlarmUseCase
import com.digitalclockpro.domain.usecase.ObserveAlarmsUseCase
import com.digitalclockpro.domain.usecase.ToggleAlarmUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlarmListUiState(
    val alarms: List<Alarm> = emptyList(),
    val use24Hour: Boolean = false
)

@HiltViewModel
class AlarmListViewModel @Inject constructor(
    observeAlarms: ObserveAlarmsUseCase,
    preferencesRepository: PreferencesRepository,
    private val toggleAlarm: ToggleAlarmUseCase,
    private val deleteAlarm: DeleteAlarmUseCase
) : ViewModel() {

    val uiState: StateFlow<AlarmListUiState> =
        combine(observeAlarms(), preferencesRepository.preferences) { alarms, prefs ->
            AlarmListUiState(alarms, prefs.use24Hour)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmListUiState())

    fun onToggle(id: Long, enabled: Boolean) = viewModelScope.launch { toggleAlarm(id, enabled) }
    fun onDelete(id: Long) = viewModelScope.launch { deleteAlarm(id) }
}
