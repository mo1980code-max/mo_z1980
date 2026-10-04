package com.digitalclockpro.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.model.UserPreferences
import com.digitalclockpro.domain.repository.PreferencesRepository
import com.digitalclockpro.domain.usecase.ObserveAlarmsUseCase
import com.digitalclockpro.domain.usecase.ToggleAlarmUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val preferences: UserPreferences = UserPreferences(),
    val nextAlarm: Alarm? = null,
    val enabledAlarmCount: Int = 0
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    observeAlarms: ObserveAlarmsUseCase,
    preferencesRepository: PreferencesRepository,
    private val toggleAlarm: ToggleAlarmUseCase
) : ViewModel() {

    val uiState: StateFlow<DashboardUiState> =
        combine(observeAlarms(), preferencesRepository.preferences) { alarms, prefs ->
            val enabled = alarms.filter { it.enabled }
            DashboardUiState(
                preferences = prefs,
                nextAlarm = enabled.minByOrNull { it.nextTriggerAtMillis() },
                enabledAlarmCount = enabled.size
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState())

    fun onToggleNextAlarm(enabled: Boolean) {
        val id = uiState.value.nextAlarm?.id ?: return
        viewModelScope.launch { toggleAlarm(id, enabled) }
    }
}
