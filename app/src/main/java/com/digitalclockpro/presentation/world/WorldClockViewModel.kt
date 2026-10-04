package com.digitalclockpro.presentation.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.domain.model.CityCatalogEntry
import com.digitalclockpro.domain.model.SavedCity
import com.digitalclockpro.domain.repository.PreferencesRepository
import com.digitalclockpro.domain.usecase.AddCityUseCase
import com.digitalclockpro.domain.usecase.ObserveSavedCitiesUseCase
import com.digitalclockpro.domain.usecase.RemoveCityUseCase
import com.digitalclockpro.domain.usecase.SearchCitiesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

data class WorldClockUiState(
    val cities: List<SavedCity> = emptyList(),
    val homeZone: ZoneId = ZoneId.systemDefault(),
    val use24Hour: Boolean = false,
    /** Time-travel offset in minutes applied to every card (-24h … +24h). */
    val travelOffsetMinutes: Int = 0
)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class WorldClockViewModel @Inject constructor(
    observeSavedCities: ObserveSavedCitiesUseCase,
    preferencesRepository: PreferencesRepository,
    private val searchCities: SearchCitiesUseCase,
    private val addCity: AddCityUseCase,
    private val removeCity: RemoveCityUseCase
) : ViewModel() {

    private val travelOffset = MutableStateFlow(0)
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<WorldClockUiState> = combine(
        observeSavedCities(),
        preferencesRepository.preferences,
        travelOffset
    ) { cities, prefs, offset ->
        WorldClockUiState(
            cities = cities,
            homeZone = prefs.homeZoneId?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: ZoneId.systemDefault(),
            use24Hour = prefs.use24Hour,
            travelOffsetMinutes = offset
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WorldClockUiState())

    /** Debounced offline search over the 10k+ city catalogue. */
    val searchResults: StateFlow<List<CityCatalogEntry>> = _query
        .debounce(180)
        .distinctUntilChanged()
        .flatMapLatest { q -> flow { emit(searchCities(q)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onQueryChange(value: String) { _query.value = value }
    fun onTravelOffsetChange(minutes: Int) { travelOffset.value = minutes.coerceIn(-1440, 1440) }
    fun resetTravel() { travelOffset.value = 0 }
    fun onAddCity(entry: CityCatalogEntry) = viewModelScope.launch {
        addCity(entry); _query.value = ""
    }
    fun onRemoveCity(id: Long) = viewModelScope.launch { removeCity(id) }
}
