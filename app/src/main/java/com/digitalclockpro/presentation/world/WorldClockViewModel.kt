package com.digitalclockpro.presentation.world

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.clockengine.MeetingPlanner
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
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class WorldClockUiState(
    val cities: List<SavedCity> = emptyList(),
    val homeZone: ZoneId = ZoneId.systemDefault(),
    val use24Hour: Boolean = false,
    /** Time-travel offset in minutes applied to every card (-24h … +24h). */
    val travelOffsetMinutes: Int = 0
)

/** Working-hours window used by the meeting planner, editable by the user. */
data class WorkingHours(
    val startHour: Int = MeetingPlanner.DEFAULT_WORK_START_HOUR,
    val endHour: Int = MeetingPlanner.DEFAULT_WORK_END_HOUR
)

data class MeetingPlanState(
    /** Cities taking part, always including the user's own zone as the first participant. */
    val participants: List<SavedCity> = emptyList(),
    val windows: List<MeetingPlanner.Window> = emptyList(),
    val workingHours: WorkingHours = WorkingHours(),
    val day: LocalDate = LocalDate.now()
) {
    val hasEnoughCities: Boolean get() = participants.size >= 2
    val hasPerfectWindow: Boolean get() = windows.any { it.isUnanimous }
}

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

    /** Cities ticked for the side-by-side comparison row and the meeting planner. */
    private val _comparisonIds = MutableStateFlow<Set<Long>>(emptySet())
    val comparisonIds: StateFlow<Set<Long>> = _comparisonIds.asStateFlow()

    private val _workingHours = MutableStateFlow(WorkingHours())
    private val _plannerDay = MutableStateFlow(LocalDate.now())

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

    /** The cities currently shown side by side — capped at [MAX_COMPARISON] so a row stays readable. */
    val comparison: StateFlow<List<SavedCity>> = combine(
        uiState, _comparisonIds
    ) { state, ids ->
        val selected = state.cities.filter { it.id in ids }
        // Nothing ticked yet: show the first few saved cities so the feature is never empty.
        selected.ifEmpty { state.cities }.take(MAX_COMPARISON)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Recomputed whenever the selection, the working hours or the chosen day changes.
     *
     * The user's own zone is injected as a synthetic participant so the answer is "when can *I*
     * meet them", which is the only question worth asking here.
     */
    val meetingPlan: StateFlow<MeetingPlanState> = combine(
        uiState, comparison, _workingHours, _plannerDay
    ) { state, cities, hours, day ->
        val me = SavedCity(
            id = HOME_PARTICIPANT_ID,
            cityName = "",                       // labelled from strings in the UI
            country = "",
            zoneId = state.homeZone.id,
            isHome = true
        )
        val participants = listOf(me) + cities.filter { it.zoneId != state.homeZone.id }

        val windows = if (participants.size < 2) emptyList() else MeetingPlanner.bestWindows(
            participants = participants.map {
                MeetingPlanner.Participant(
                    id = it.id.toString(),
                    zone = runCatching { it.zone }.getOrDefault(state.homeZone),
                    workStartHour = hours.startHour,
                    workEndHour = hours.endHour
                )
            },
            day = day,
            anchorZone = state.homeZone
        )

        MeetingPlanState(participants, windows, hours, day)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MeetingPlanState())

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

    fun onRemoveCity(id: Long) = viewModelScope.launch {
        _comparisonIds.value = _comparisonIds.value - id
        removeCity(id)
    }

    /** Ticks a city into the comparison row; silently ignored once [MAX_COMPARISON] are picked. */
    fun toggleComparison(id: Long) {
        val current = _comparisonIds.value
        _comparisonIds.value = when {
            id in current -> current - id
            current.size >= MAX_COMPARISON -> current
            else -> current + id
        }
    }

    fun clearComparison() { _comparisonIds.value = emptySet() }

    fun setWorkingHours(startHour: Int, endHour: Int) {
        // Guard the inverted case here so the engine never has to.
        val start = startHour.coerceIn(0, 23)
        val end = endHour.coerceIn(start + 1, 24)
        _workingHours.value = WorkingHours(start, end)
    }

    fun shiftPlannerDay(days: Long) {
        _plannerDay.value = _plannerDay.value.plusDays(days)
    }

    fun resetPlannerDay() { _plannerDay.value = LocalDate.now() }

    companion object {
        /** Four cities is the most that stays legible in one row on a phone. */
        const val MAX_COMPARISON = 4

        /** Sentinel id for the synthetic "you" participant; no saved city can have it. */
        const val HOME_PARTICIPANT_ID = -1L
    }
}
