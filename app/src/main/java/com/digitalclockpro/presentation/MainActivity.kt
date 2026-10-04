package com.digitalclockpro.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.digitalclockpro.ads.AdLaunchOrigins
import com.digitalclockpro.ads.AdsController
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

    @Inject lateinit var adsController: AdsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // UMP consent FIRST, MobileAds.initialize only after it allows ads. MainActivity is
        // the ad-hosting activity, so the consent form only ever appears over ad-friendly
        // screens — never over the alarm ringing screen, desk clock or widget studio.
        adsController.gatherConsentAndInitialize(this)
        // An open that came FROM an alarm (status-bar alarm icon / notification) must never
        // be greeted with an app-open ad; the veto itself lives in AdPolicy.
        adsController.onLaunched(AdLaunchOrigins.fromIntent(intent))

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
                DigitalClockProApp(
                    startDestination = startDestination,
                    adsController = adsController
                )
            }
        }
    }

    /**
     * `singleTask`: re-deliveries (alarm icon tap while the app is running) arrive here.
     * Refresh the launch origin so the next foreground transition is classified correctly.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        adsController.onLaunched(AdLaunchOrigins.fromIntent(intent))
    }
}
