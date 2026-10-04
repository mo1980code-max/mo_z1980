package com.digitalclockpro.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digitalclockpro.core.ui.theme.DigitalClockProTheme
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.domain.model.UserPreferences
import com.digitalclockpro.domain.repository.PreferencesRepository
import com.digitalclockpro.presentation.navigation.TopLevelDestination
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    preferencesRepository: PreferencesRepository
) : ViewModel() {
    val preferences: StateFlow<UserPreferences> = preferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val startDestination = TopLevelDestination.fromRoute(
            intent?.getStringExtra(AppIntents.EXTRA_START_DESTINATION)
        )

        setContent {
            val appViewModel: AppViewModel = viewModel()
            val prefs by appViewModel.preferences.collectAsStateWithLifecycle()

            DigitalClockProTheme(
                themeMode = prefs.themeMode,
                dynamicColor = prefs.dynamicColor,
                amoledBlack = prefs.amoledBlack,
                accentColor = prefs.accentColor.takeIf { !prefs.dynamicColor }
            ) {
                DigitalClockProApp(startDestination = startDestination)
            }
        }
    }
}
