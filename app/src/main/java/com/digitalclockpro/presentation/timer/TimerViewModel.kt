package com.digitalclockpro.presentation.timer

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.clockengine.StopwatchEngine
import com.digitalclockpro.clockengine.TimerEngine
import com.digitalclockpro.data.ringtone.TimerAlertPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives both the countdown timer and the stopwatch.
 *
 * All the logic lives in the pure `:clock-engine` module; this class only supplies the monotonic
 * clock, runs the redraw loop and owns the alert sound. `SystemClock.elapsedRealtime()` is used
 * everywhere instead of wall time, so changing the device clock or crossing a timezone cannot
 * corrupt a running timer.
 */
@HiltViewModel
class TimerViewModel @Inject constructor(
    private val alertPlayer: TimerAlertPlayer
) : ViewModel() {

    private val _timer = MutableStateFlow(TimerEngine.State())
    val timer: StateFlow<TimerEngine.State> = _timer.asStateFlow()

    private val _stopwatch = MutableStateFlow(StopwatchEngine.State())
    val stopwatch: StateFlow<StopwatchEngine.State> = _stopwatch.asStateFlow()

    /** Re-emitted every frame so the UI recomputes elapsed/remaining without storing it twice. */
    private val _nowElapsed = MutableStateFlow(SystemClock.elapsedRealtime())
    val nowElapsed: StateFlow<Long> = _nowElapsed.asStateFlow()

    private var tickJob: Job? = null

    // ------------------------------------------------------------------ timer

    fun setDuration(hours: Int, minutes: Int, seconds: Int) {
        val millis = (hours * 3600L + minutes * 60L + seconds) * 1000L
        _timer.update { TimerEngine.setDuration(it, millis) }
    }

    /** Adds a preset chunk (+1 min, +5 min …), also while the timer is running. */
    fun addMinutes(minutes: Int) {
        _timer.update { TimerEngine.adjust(it, minutes * 60_000L, now()) }
        if (_timer.value.isRunning) ensureTicking()
    }

    fun startOrPause() {
        val state = _timer.value
        _timer.value = if (state.isRunning) {
            TimerEngine.pause(state, now())
        } else {
            TimerEngine.start(state, now())
        }
        ensureTicking()
    }

    fun resetTimer() {
        alertPlayer.stop()
        _timer.update { TimerEngine.reset(it) }
    }

    /** Silences a finished timer and rearms it with the same duration. */
    fun dismissAlert() {
        alertPlayer.stop()
        _timer.update { TimerEngine.dismiss(it) }
    }

    // ------------------------------------------------------------------ stopwatch

    fun toggleStopwatch() {
        _stopwatch.update { StopwatchEngine.toggle(it, now()) }
        ensureTicking()
    }

    fun lap() = _stopwatch.update { StopwatchEngine.lap(it, now()) }

    fun resetStopwatch() {
        _stopwatch.value = StopwatchEngine.reset()
    }

    // ------------------------------------------------------------------ ticking

    /**
     * One shared loop for both features, started only while something is actually running and
     * cancelled as soon as both are idle — an always-on 60 fps loop would be a battery bug.
     */
    private fun ensureTicking() {
        val needed = _timer.value.isRunning || _stopwatch.value.running
        if (!needed) {
            tickJob?.cancel()
            tickJob = null
            return
        }
        if (tickJob?.isActive == true) return

        tickJob = viewModelScope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                _nowElapsed.value = now

                val before = _timer.value
                val after = TimerEngine.tick(before, now)
                if (after != before) _timer.value = after
                // Fire exactly once, on the RUNNING -> FINISHED edge.
                if (before.phase != TimerEngine.Phase.FINISHED &&
                    after.phase == TimerEngine.Phase.FINISHED
                ) {
                    alertPlayer.start()
                }

                if (!_timer.value.isRunning && !_stopwatch.value.running) break
                // 50 ms keeps the stopwatch centiseconds smooth without burning a frame budget.
                delay(TICK_INTERVAL_MILLIS)
            }
            tickJob = null
        }
    }

    private fun now() = SystemClock.elapsedRealtime()

    override fun onCleared() {
        tickJob?.cancel()
        alertPlayer.stop()
        super.onCleared()
    }

    private companion object { const val TICK_INTERVAL_MILLIS = 50L }
}
