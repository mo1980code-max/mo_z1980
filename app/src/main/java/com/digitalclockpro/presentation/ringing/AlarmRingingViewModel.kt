package com.digitalclockpro.presentation.ringing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.alarm.AlarmSessionManager
import com.digitalclockpro.alarm.challenge.MathChallengeGenerator
import com.digitalclockpro.alarm.challenge.MathProblem
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.model.DismissChallenge
import com.digitalclockpro.domain.repository.AlarmRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RingingUiState(
    val alarm: Alarm? = null,
    val problems: List<MathProblem> = emptyList(),
    val problemIndex: Int = 0,
    val shakeProgress: Int = 0,
    val sequenceTiles: List<Int> = emptyList(),
    val sequenceProgress: Int = 0,
    val error: String? = null,
    val solved: Boolean = false,
    /** True once the service reports that nothing is ringing — the screen should close. */
    val finished: Boolean = false
) {
    val snoozesLeft: Int
        get() = ((alarm?.maxSnoozeCount ?: 0) - (alarm?.currentSnoozeCount ?: 0)).coerceAtLeast(0)
    val currentProblem: MathProblem? get() = problems.getOrNull(problemIndex)
}

/**
 * Collects the ringing state from [AlarmSessionManager] — the decoupled source of truth owned by
 * the domain/service layer — instead of reaching into a static flow on `AlarmService`.
 *
 * Benefits: no static state to leak, the screen repopulates correctly after process death, and
 * the ViewModel is unit-testable with a fake session manager.
 */
@HiltViewModel
class AlarmRingingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessionManager: AlarmSessionManager,
    private val alarmRepository: AlarmRepository
) : ViewModel() {

    /** Only used as a fallback when the session has not been published yet. */
    private val requestedAlarmId: Long =
        savedStateHandle[AlarmRingingActivity.EXTRA_ALARM_ID] ?: -1L

    private val _state = MutableStateFlow(RingingUiState())
    val state: StateFlow<RingingUiState> = _state.asStateFlow()

    /** Challenge data is generated once per alarm, never regenerated on recomposition. */
    private var preparedForAlarmId: Long = -1L

    init {
        viewModelScope.launch {
            sessionManager.session.collect { session ->
                when {
                    session != null -> prepareFor(session.alarm)
                    // Session cleared while we were showing: the alarm was snoozed/dismissed
                    // elsewhere (notification action, auto-silence) -> close the screen.
                    preparedForAlarmId > 0L -> _state.update { it.copy(finished = true) }
                    // Cold start before the service published its session (e.g. the activity won
                    // the race): fall back to the id supplied in the intent.
                    else -> loadFallback()
                }
            }
        }
    }

    private suspend fun loadFallback() {
        if (requestedAlarmId <= 0L || preparedForAlarmId > 0L) return
        alarmRepository.getAlarm(requestedAlarmId)?.let { prepareFor(it) }
    }

    private fun prepareFor(alarm: Alarm) {
        if (preparedForAlarmId == alarm.id) {
            // Same alarm, refreshed entity (e.g. snooze counter changed): keep challenge progress.
            _state.update { it.copy(alarm = alarm) }
            return
        }
        preparedForAlarmId = alarm.id
        _state.value = when (val challenge = alarm.challenge) {
            is DismissChallenge.Math -> RingingUiState(
                alarm = alarm,
                problems = MathChallengeGenerator.generate(challenge.difficulty, challenge.problemCount)
            )
            is DismissChallenge.Sequence -> RingingUiState(
                alarm = alarm,
                sequenceTiles = (1..challenge.tiles).shuffled()
            )
            else -> RingingUiState(alarm = alarm)
        }
    }

    fun submitMathAnswer(input: String) {
        val expected = _state.value.currentProblem?.answer ?: return
        if (input.trim().toIntOrNull() == expected) {
            _state.update {
                val nextIndex = it.problemIndex + 1
                it.copy(
                    problemIndex = nextIndex,
                    error = null,
                    solved = nextIndex >= it.problems.size
                )
            }
        } else {
            _state.update { it.copy(error = "Wrong answer, try again") }
        }
    }

    fun onShake(count: Int) {
        val required = (_state.value.alarm?.challenge as? DismissChallenge.Shake)?.shakeCount ?: return
        _state.update { it.copy(shakeProgress = count, solved = count >= required) }
    }

    fun onSequenceTap(value: Int) {
        _state.update { current ->
            val expected = current.sequenceProgress + 1
            if (value == expected) {
                val next = current.sequenceProgress + 1
                current.copy(
                    sequenceProgress = next,
                    error = null,
                    solved = next >= current.sequenceTiles.size
                )
            } else {
                current.copy(sequenceProgress = 0, error = "Start over from 1")
            }
        }
    }
}
