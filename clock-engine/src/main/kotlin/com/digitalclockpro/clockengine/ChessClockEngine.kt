package com.digitalclockpro.clockengine

/**
 * Two opposed countdown clocks with tournament time controls.
 *
 * Like the rest of this module it is pure and framework-free: the caller supplies a monotonic
 * timestamp (`SystemClock.elapsedRealtime()` on Android) and receives a new immutable [State].
 * A monotonic source is not a nicety here — a blitz game is decided by tenths of a second, and
 * a wall-clock jump or an NTP correction mid-game would silently hand someone the win.
 *
 * Only the clock of the player **on move** is ever running, and only that player's press ends a
 * turn, exactly like a physical clock: pressing your opponent's lever while it is their move
 * does nothing.
 */
object ChessClockEngine {

    /** The two sides. Named A/B rather than White/Black: the clock does not know who is who. */
    enum class Player { A, B;
        val opponent: Player get() = if (this == A) B else A
    }

    enum class Phase { IDLE, RUNNING, PAUSED, FINISHED }

    /**
     * Why a finished game ended. A clock cannot see checkmate — it knows time, and it knows
     * when both players agree to stop.
     */
    enum class EndReason {
        /** The player on move ran their clock to zero. */
        TIME_OUT,
        /** The game was ended early; the player on move conceded. */
        RESIGN
    }

    /**
     * The four controls real tournaments use.
     *
     * [SIMPLE_DELAY] and [BRONSTEIN] are **not** the same mechanism even though they are often
     * conflated: they leave you with identical time on the clock, but they display it
     * differently during the move, and players steer by what they see.
     */
    enum class TimeControlType {
        /** A fixed allowance. When it is gone, you lose on time. */
        SUDDEN_DEATH,

        /** Fischer: the bonus is credited **after** every completed move, and it accumulates. */
        FISCHER_INCREMENT,

        /** US/simple delay: the clock stands still for `bonusMillis`, then starts counting. */
        SIMPLE_DELAY,

        /** Bronstein: the clock runs at once, then the time used is refunded up to `bonusMillis`. */
        BRONSTEIN
    }

    /**
     * @param bonusMillis increment for [FISCHER_INCREMENT], delay for the two delay modes,
     *   ignored for [SUDDEN_DEATH].
     */
    data class TimeControl(
        val type: TimeControlType = TimeControlType.SUDDEN_DEATH,
        val baseMillis: Long = 5 * 60_000L,
        val bonusMillis: Long = 0L
    ) {
        val safeBase: Long get() = baseMillis.coerceIn(1_000L, MAX_BASE_MILLIS)
        val safeBonus: Long get() = bonusMillis.coerceIn(0L, MAX_BONUS_MILLIS)
    }

    data class PlayerState(
        /** Time banked at the last settle point; the live value comes from [remaining]. */
        val storedMillis: Long,
        val moves: Int = 0,
        /**
         * Total time this player has actually been **charged** across the whole game — the sum
         * of every settled turn, delay discounts included. Unlike [storedMillis] it never
         * decreases (increments and refunds do not pad it), which is what makes it the honest
         * "thinking time" the post-game summary reports. Maintained by [settle]; frozen once
         * the game is [Phase.FINISHED].
         */
        val spentMillis: Long = 0L
    )

    data class State(
        val control: TimeControl = TimeControl(),
        val a: PlayerState = PlayerState(TimeControl().safeBase),
        val b: PlayerState = PlayerState(TimeControl().safeBase),
        val active: Player? = null,
        val phase: Phase = Phase.IDLE,
        /** Monotonic instant at which the current turn began. Meaningless unless RUNNING. */
        val turnStartedAtElapsed: Long = 0L,
        /** Who ran out of time, once the game is over. */
        val flagged: Player? = null,
        /** How the game ended; null while it has not. */
        val endedBy: EndReason? = null
    ) {
        val isRunning: Boolean get() = phase == Phase.RUNNING
        val totalMoves: Int get() = a.moves + b.moves

        fun player(side: Player): PlayerState = if (side == Player.A) a else b

        internal fun withPlayer(side: Player, value: PlayerState): State =
            if (side == Player.A) copy(a = value) else copy(b = value)
    }

    /**
     * The post-game report [summarize] produces. Numbers only — names, plurals and units are
     * the presentation layer's job, the same rule the rest of this module follows.
     */
    data class GameSummary(
        val control: TimeControl,
        val winner: Player,
        val loser: Player,
        val endedBy: EndReason,
        val a: PlayerTally,
        val b: PlayerTally
    ) {
        data class PlayerTally(
            val moves: Int,
            val spentMillis: Long,
            /** Banked time at the end; zero for the player who flagged. */
            val remainingMillis: Long
        ) {
            /** Mean charged time per completed move; null when no move was completed. */
            val averageMillisPerMove: Long? get() = if (moves > 0) spentMillis / moves else null
        }

        /** Active playing time: both players' charged thinking time combined. */
        val activeMillis: Long get() = a.spentMillis + b.spentMillis
    }

    // ------------------------------------------------------------------ queries

    /**
     * Live time left for [side]. Never negative — a flagged clock reads 0, not -400 ms.
     */
    fun remaining(state: State, side: Player, nowElapsed: Long): Long {
        val stored = state.player(side).storedMillis
        if (state.phase != Phase.RUNNING || state.active != side) {
            return stored.coerceAtLeast(0L)
        }
        return (stored - consumedThisTurn(state, nowElapsed)).coerceAtLeast(0L)
    }

    /** True once [side] is inside the last [CRITICAL_THRESHOLD_MILLIS] — drives the red UI. */
    fun isCritical(
        state: State,
        side: Player,
        nowElapsed: Long,
        thresholdMillis: Long = CRITICAL_THRESHOLD_MILLIS
    ): Boolean {
        if (state.phase == Phase.IDLE) return false
        return remaining(state, side, nowElapsed) <= thresholdMillis
    }

    /**
     * Freezes the finished game into a [GameSummary]. Null unless the game is actually over —
     * the UI treats a non-null result as the only thing worth putting on a game-over screen,
     * and it is the *reward* the post-game rewarded ad unlocks.
     */
    fun summarize(state: State): GameSummary? {
        if (state.phase != Phase.FINISHED) return null
        val loser = state.flagged ?: return null
        return GameSummary(
            control = state.control,
            winner = loser.opponent,
            loser = loser,
            endedBy = state.endedBy ?: EndReason.TIME_OUT,
            a = GameSummary.PlayerTally(state.a.moves, state.a.spentMillis, state.a.storedMillis),
            b = GameSummary.PlayerTally(state.b.moves, state.b.spentMillis, state.b.storedMillis)
        )
    }

    /**
     * How much of the current turn has actually been charged.
     *
     * Under [TimeControlType.SIMPLE_DELAY] the first `bonusMillis` of every turn are free, so the
     * clock visibly freezes; every other control charges from the first millisecond.
     */
    private fun consumedThisTurn(state: State, nowElapsed: Long): Long {
        val elapsed = (nowElapsed - state.turnStartedAtElapsed).coerceAtLeast(0L)
        return if (state.control.type == TimeControlType.SIMPLE_DELAY) {
            (elapsed - state.control.safeBonus).coerceAtLeast(0L)
        } else {
            elapsed
        }
    }

    // ------------------------------------------------------------------ transitions

    /** A fresh game on [control]; both clocks full, nobody on move. */
    fun reset(control: TimeControl): State = State(
        control = control,
        a = PlayerState(control.safeBase),
        b = PlayerState(control.safeBase),
        active = null,
        phase = Phase.IDLE,
        turnStartedAtElapsed = 0L,
        flagged = null
    )

    /** Changing the control always restarts the game — a half-played game under new rules is nonsense. */
    fun setControl(state: State, control: TimeControl): State = reset(control)

    /**
     * Starts the game with [first] on move. Ignored unless the game is idle, so a stray tap on
     * the board cannot restart a running game.
     */
    fun start(state: State, first: Player, nowElapsed: Long): State {
        if (state.phase != Phase.IDLE) return state
        return state.copy(
            active = first,
            phase = Phase.RUNNING,
            turnStartedAtElapsed = nowElapsed,
            flagged = null
        )
    }

    /**
     * [side] completes their move and hands over the clock.
     *
     * Ignored unless [side] is the player on move, matching a physical clock. The bonus is only
     * credited if the move was actually completed in time: flagging on your own move does not
     * earn you an increment.
     */
    fun press(state: State, side: Player, nowElapsed: Long): State {
        if (state.phase != Phase.RUNNING || state.active != side) return state

        val settled = settle(state, nowElapsed)
        if (settled.phase == Phase.FINISHED) return settled

        val rawElapsed = (nowElapsed - state.turnStartedAtElapsed).coerceAtLeast(0L)
        val bonus = when (state.control.type) {
            TimeControlType.FISCHER_INCREMENT -> state.control.safeBonus
            // Bronstein refunds what you actually used, never more than the delay.
            TimeControlType.BRONSTEIN -> minOf(rawElapsed, state.control.safeBonus)
            TimeControlType.SIMPLE_DELAY, TimeControlType.SUDDEN_DEATH -> 0L
        }

        val mover = settled.player(side)
        return settled
            .withPlayer(
                side,
                mover.copy(
                    storedMillis = (mover.storedMillis + bonus).coerceAtMost(MAX_BASE_MILLIS),
                    moves = mover.moves + 1
                )
            )
            .copy(active = side.opponent, turnStartedAtElapsed = nowElapsed)
    }

    /** Freezes both clocks. The banked time is settled first so no millisecond is lost or gained. */
    fun pause(state: State, nowElapsed: Long): State {
        if (state.phase != Phase.RUNNING) return state
        val settled = settle(state, nowElapsed)
        if (settled.phase == Phase.FINISHED) return settled
        return settled.copy(phase = Phase.PAUSED)
    }

    fun resume(state: State, nowElapsed: Long): State {
        if (state.phase != Phase.PAUSED || state.active == null) return state
        return state.copy(phase = Phase.RUNNING, turnStartedAtElapsed = nowElapsed)
    }

    /**
     * Ends the game early, resignation-style: the player **on move** is recorded as the loser.
     *
     * A clock cannot see checkmate or a formal resignation — but players of a casual game want
     * the same end-of-game flow (result screen, post-game summary) when they agree to stop, and
     * "the player to move conceded" is the convention this records. The running turn is settled
     * first so the tally charges every millisecond actually used. Ignored unless a game is in
     * progress, so a stray call can never end an idle or already-finished board.
     */
    fun finish(state: State, nowElapsed: Long): State {
        if (state.phase != Phase.RUNNING && state.phase != Phase.PAUSED) return state
        val loser = state.active ?: return state
        val settled = settle(state, nowElapsed)
        if (settled.phase == Phase.FINISHED) return settled // they ran out while ending it
        return settled.copy(phase = Phase.FINISHED, flagged = loser, endedBy = EndReason.RESIGN)
    }

    /** Call from the UI frame loop; the only place a flag fall is noticed while nobody presses. */
    fun tick(state: State, nowElapsed: Long): State {
        if (state.phase != Phase.RUNNING) return state
        val side = state.active ?: return state
        if (remaining(state, side, nowElapsed) > 0L) return state
        return settle(state, nowElapsed)
    }

    /**
     * Charges the active player for the time used so far and re-bases the turn at [nowElapsed].
     * Marks the game finished if that exhausts their clock. Also adds the charge to the
     * player's `spentMillis` tally, so the post-game summary can be derived from the state alone.
     */
    private fun settle(state: State, nowElapsed: Long): State {
        val side = state.active ?: return state
        val left = remaining(state, side, nowElapsed)
        val updated = state
            .withPlayer(
                side,
                state.player(side).copy(
                    storedMillis = left,
                    spentMillis = state.player(side).spentMillis + consumedThisTurn(state, nowElapsed)
                )
            )
            .copy(turnStartedAtElapsed = nowElapsed)

        return if (left <= 0L) {
            updated.copy(phase = Phase.FINISHED, flagged = side, endedBy = EndReason.TIME_OUT)
        } else {
            updated
        }
    }

    // ------------------------------------------------------------------ presets

    /**
     * The controls players actually ask for. No display names here — they are translated in the
     * presentation layer, the same rule the rest of this project follows.
     */
    enum class Preset(val control: TimeControl) {
        BULLET_1_0(TimeControl(TimeControlType.SUDDEN_DEATH, 60_000L)),
        BULLET_2_1(TimeControl(TimeControlType.FISCHER_INCREMENT, 2 * 60_000L, 1_000L)),
        BLITZ_3_2(TimeControl(TimeControlType.FISCHER_INCREMENT, 3 * 60_000L, 2_000L)),
        BLITZ_5_0(TimeControl(TimeControlType.SUDDEN_DEATH, 5 * 60_000L)),
        RAPID_10_0(TimeControl(TimeControlType.SUDDEN_DEATH, 10 * 60_000L)),
        RAPID_15_10(TimeControl(TimeControlType.FISCHER_INCREMENT, 15 * 60_000L, 10_000L)),
        CLASSICAL_30_30(TimeControl(TimeControlType.FISCHER_INCREMENT, 30 * 60_000L, 30_000L))
    }

    /** Conventional "base+bonus" shorthand, e.g. "3+2". Digits only, so it needs no translation. */
    fun shorthand(control: TimeControl): String {
        val minutes = control.safeBase / 60_000L
        val seconds = (control.safeBase % 60_000L) / 1000L
        val base = if (seconds == 0L) "$minutes" else "$minutes:%02d".format(seconds)
        return "$base+${control.safeBonus / 1000L}"
    }

    const val CRITICAL_THRESHOLD_MILLIS = 10_000L
    const val MAX_BASE_MILLIS = 6L * 60 * 60 * 1000       // 6 hours covers classical games
    const val MAX_BONUS_MILLIS = 5L * 60 * 1000
}
