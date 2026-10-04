package com.digitalclockpro.presentation.settings.oem

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.data.prefs.OemChecklistDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OemSetupViewModel @Inject constructor(
    private val checklist: OemChecklistDataSource
) : ViewModel() {

    val completedSteps: StateFlow<Set<String>> = checklist.completedSteps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    fun setStepCompleted(id: String, completed: Boolean) = viewModelScope.launch {
        checklist.setCompleted(id, completed)
    }
}
