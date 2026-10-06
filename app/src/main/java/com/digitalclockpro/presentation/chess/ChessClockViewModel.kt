package com.digitalclockpro.presentation.chess

import android.app.Activity
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.ads.RewardedAdManager
import com.digitalclockpro.clockengine.AdSurface
import com.digitalclockpro.clockengine.ChessClockEngine
import com.digitalclockpro.data.sound.ChessClockFeedback
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChessClockPrefs(
    val soundEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true
)

@HiltViewModel
class ChessClockViewModel @Inject constructor(
    private val feedback: ChessClockFeedback,
    private val rewardedAds: RewardedAdManager
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChessClockEngine.reset(ChessClockEngine.Preset.BLITZ_3_2.control)
    )
    val state: StateFlow<ChessClockEngine.State> = _state.asStateFlow()

    private val _prefs = MutableStateFlow(ChessClockPrefs())
    val prefs: StateFlow<ChessClockPrefs> = _prefs.asStateFlow()

    /** Re-emitted every frame so the two clocks redraw without duplicating their value in state. */
    private val _now = MutableStateFlow(SystemClock.elapsedRealtime())
    val now: StateFlow<Long> = _now.asStateFlow()

    /**
     * The reward: the post-game summary stays hidden until the user has *earned* it by
     * watching the rewarded ad through to `onUserEarnedReward`. Lives here — not in composable
     * state — so a rotation mid-ad cannot lose something the user already paid for with their
     * time. Cleared the moment a new game begins.
     */
    private val _summaryUnlocked = MutableStateFlow(false)
    val summaryUnlocked: StateFlow<Boolean> = _summaryUnlocked.asStateFlow()

    /** True right after the ad proved unavailable; the UI shows a gentle notice and clears it. */
    private val _adUnavailableNotice = MutableStateFlow(false)
    val adUnavailableNotice: StateFlow<Boolean> = _adUnavailableNotice.asStateFlow()

    private var tickJob: Job? = null

    /** Remembers who has already been warned, so the buzz fires once per clock, not every frame. */
    private val warned = mutableSetOf<ChessClockEngine.Player>()

    init {
        feedback.prepare()
    }

    /**
     * A tap on a player's half.
     *
     * The first tap starts the game with the *opponent* on move — tapping your own side is how
     * you say "I am ready, your move", which is how a real clock is started.
     */
    fun onTap(side: ChessClockEngine.Player) {
        val current = _state.value
        val now = SystemClock.elapsedRealtime()

        val next = when (current.phase) {
            ChessClockEngine.Phase.IDLE ->
                ChessClockEngine.start(current, side.opponent, now)
            ChessClockEngine.Phase.RUNNING ->
                ChessClockEngine.press(current, side, now)
            // A tap on a paused or finished board does nothing; use the explicit buttons.
            else -> current
        }

        if (next !== current) {
            _state.value = next
            warned.remove(side)
            feedback.click(_prefs.value.soundEnabled, _prefs.value.hapticsEnabled)
            ensureTicking()
        }
    }

    fun pauseOrResume() {
        val now = SystemClock.elapsedRealtime()
        _state.value = when (_state.value.phase) {
            ChessClockEngine.Phase.RUNNING -> ChessClockEngine.pause(_state.value, now)
            ChessClockEngine.Phase.PAUSED -> ChessClockEngine.resume(_state.value, now)
            else -> _state.value
        }
        ensureTicking()
    }

    fun reset() {
        _state.value = ChessClockEngine.reset(_state.value.control)
        warned.clear()
        clearGameRewardState()
        ensureTicking()
    }

    fun applyPreset(preset: ChessClockEngine.Preset) {
        _state.value = ChessClockEngine.setControl(_state.value, preset.control)
        warned.clear()
        clearGameRewardState()
        ensureTicking()
    }

    fun applyCustom(minutes: Int, incrementSeconds: Int, type: ChessClockEngine.TimeControlType) {
        val control = ChessClockEngine.TimeControl(
            type = type,
            baseMillis = minutes.coerceIn(1, 360) * 60_000L,
            bonusMillis = incrementSeconds.coerceIn(0, 300) * 1_000L
        )
        _state.value = ChessClockEngine.setControl(_state.value, control)
        warned.clear()
        clearGameRewardState()
        ensureTicking()
    }

    /**
     * Resignation-style early end, from the "End game" control. The engine settles and freezes
     * both clocks; the game-over dialog (and with it the rewarded offer) follows from the
     * FINISHED phase exactly as it does after a flag fall.
     */
    fun endGame() {
        _state.value = ChessClockEngine.finish(_state.value, SystemClock.elapsedRealtime())
        ensureTicking() // cancels the loop: nothing is running any more
    }

    /**
     * The user tapped "watch an ad" on the game-over dialog. Everything about whether an ad
     * may appear is [com.digitalclockpro.clockengine.AdPolicy]'s call, made inside
     * [RewardedAdManager.showIfEligible]; this only maps the two outcomes onto UI state:
     * the reward is granted strictly from `onUserEarnedReward`, and every "no ad" path raises
     * the gentle notice instead of blocking anything.
     *
     * The [activity] is used transiently to host the full-screen ad and is never stored.
     */
    fun requestMatchSummary(activity: Activity) {
        rewardedAds.showIfEligible(
            activity = activity,
            surface = AdSurface.CHESS_CLOCK,
            phase = _state.value.phase,
            onUserEarnedReward = { _summaryUnlocked.value = true },
            onAdUnavailable = { _adUnavailableNotice.value = true }
        )
    }

    /** Called by the UI once its "no ad right now" notice has been on screen long enough. */
    fun clearAdUnavailableNotice() {
        _adUnavailableNotice.value = false
    }

    /** A new game is a clean slate: last game's reward and notice do not carry over. */
    private fun clearGameRewardState() {
        _summaryUnlocked.value = false
        _adUnavailableNotice.value = false
    }

    fun toggleSound() = _prefs.update { it.copy(soundEnabled = !it.soundEnabled) }
    fun toggleHaptics() = _prefs.update { it.copy(hapticsEnabled = !it.hapticsEnabled) }

    private fun MutableStateFlow<ChessClockPrefs>.update(
        transform: (ChessClockPrefs) -> ChessClockPrefs
    ) { value = transform(value) }

    /**
     * The redraw loop, alive only while a clock is actually running.
     *
     * 100 ms is deliberate: the display shows tenths only under ten seconds, and polling faster
     * would burn battery for pixels nobody can read. Accuracy does not depend on this interval —
     * every value is derived from `elapsedRealtime()`, so a late frame shows the right number.
     */
    private fun ensureTicking() {
        if (!_state.value.isRunning) {
            tickJob?.cancel()
            tickJob = null
            return
        }
        if (tickJob?.isActive == true) return

        tickJob = viewModelScope.launch {
            while (isActive) {
                val now = SystemClock.elapsedRealtime()
                _now.value = now

                val before = _state.value
                val after = ChessClockEngine.tick(before, now)
                if (after !== before) _state.value = after

                if (after.phase == ChessClockEngine.Phase.FINISHED) {
                    feedback.flagFall(_prefs.value.hapticsEnabled)
                    break
                }

                after.active?.let { side ->
                    if (ChessClockEngine.isCritical(after, side, now) && warned.add(side)) {
                        feedback.lowTimeWarning(_prefs.value.hapticsEnabled)
                    }
                }

                if (!_state.value.isRunning) break
                delay(TICK_INTERVAL_MILLIS)
            }
            tickJob = null
        }
    }

    override fun onCleared() {
        tickJob?.cancel()
        feedback.release()
        super.onCleared()
    }

    private companion object { const val TICK_INTERVAL_MILLIS = 100L }
}
