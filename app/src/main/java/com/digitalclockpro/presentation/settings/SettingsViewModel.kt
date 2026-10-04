package com.digitalclockpro.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.domain.model.ThemeMode
import com.digitalclockpro.domain.model.UserPreferences
import com.digitalclockpro.domain.repository.PreferencesRepository
import com.digitalclockpro.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private fun update(transform: (UserPreferences) -> UserPreferences) = viewModelScope.launch {
        preferencesRepository.update(transform)
        widgetUpdater.refreshAll()
    }

    fun setThemeMode(mode: ThemeMode) = update { it.copy(themeMode = mode) }
    fun setDynamicColor(value: Boolean) = update { it.copy(dynamicColor = value) }
    fun setAmoled(value: Boolean) = update { it.copy(amoledBlack = value) }
    fun set24Hour(value: Boolean) = update { it.copy(use24Hour = value) }
    fun setShowSeconds(value: Boolean) = update { it.copy(showSecondsInApp = value) }
    fun setWeatherEnabled(value: Boolean) = update { it.copy(weatherEnabled = value) }
    fun setCelsius(value: Boolean) = update { it.copy(weatherCelsius = value) }
    fun setKeepScreenOn(value: Boolean) = update { it.copy(keepScreenOnDashboard = value) }
}
