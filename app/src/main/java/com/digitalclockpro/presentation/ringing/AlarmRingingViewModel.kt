package com.digitalclockpro.presentation.ringing

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val solved: Boolean = false
) {
    val snoozesLeft: Int get() = ((alarm?.maxSnoozeCount ?: 0) - (alarm?.currentSnoozeCount ?: 0))
        .coerceAtLeast(0)
    val currentProblem: MathProblem? get() = problems.getOrNull(problemIndex)
}

@HiltViewModel
class AlarmRingingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val alarmRepository: AlarmRepository
) : ViewModel() {

    private val alarmId: Long = savedStateHandle[AlarmRingingActivity.EXTRA_ALARM_ID] ?: -1L

    private val _state = MutableStateFlow(RingingUiState())
    val state: StateFlow<RingingUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val alarm = alarmRepository.getAlarm(alarmId) ?: return@launch
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
