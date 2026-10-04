package com.digitalclockpro.presentation.deskclock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.domain.model.UserPreferences
import com.digitalclockpro.domain.repository.PreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Supplies the desk clock with the user's real preferences.
 *
 * The screen used to hardcode `use24h = false`, which meant someone who had switched the whole
 * app to 24-hour time still got a 12-hour desk clock — the one screen they leave running all
 * night. Read-only on purpose: the desk clock has no settings of its own, it just obeys.
 */
@HiltViewModel
class DeskClockViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository
) : ViewModel() {

    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())
}
