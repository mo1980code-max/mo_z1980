package com.digitalclockpro.clockengine

/**
 * Countdown-timer state machine, pure and clock-injected.
 *
 * Nothing here reads the system clock: every transition takes the current monotonic time as a
 * parameter. That makes the whole machine deterministic under test, and — more importantly —
 * means the timer survives process death, because the state is just data plus a reference
 * timestamp rather than a running coroutine.
 *
 * Callers must pass a **monotonic** clock (`SystemClock.elapsedRealtime()`), not wall time, so
 * the countdown is immune to the user changing the device clock or to timezone jumps.
 */
object TimerEngine {

    enum class Phase { IDLE, RUNNING, PAUSED, FINISHED }

    /**
     * @param totalMillis      the configured duration.
     * @param remainingMillis  snapshot used while [Phase.PAUSED]/[Phase.IDLE].
     * @param endAtElapsed     monotonic timestamp the timer fires at, valid while running.
     */
    data class State(
        val totalMillis: Long = 0L,
        val remainingMillis: Long = 0L,
        val endAtElapsed: Long = 0L,
        val phase: Phase = Phase.IDLE
    ) {
        val isRunning: Boolean get() = phase == Phase.RUNNING
        val canStart: Boolean get() = phase != Phase.RUNNING && remainingMillis > 0L

        /** 0f..1f elapsed fraction, for the progress ring. */
        fun progress(nowElapsed: Long): Float {
            if (totalMillis <= 0L) return 0f
            val left = remaining(nowElapsed)
            return ((totalMillis - left).toFloat() / totalMillis).coerceIn(0f, 1f)
        }

        /** Milliseconds left, computed from [nowElapsed] while running. */
        fun remaining(nowElapsed: Long): Long = when (phase) {
            Phase.RUNNING -> (endAtElapsed - nowElapsed).coerceAtLeast(0L)
            Phase.FINISHED -> 0L
            else -> remainingMillis
        }
    }

    /** Sets a duration without starting. Resets a finished timer. */
    fun setDuration(state: State, millis: Long): State {
        val safe = millis.coerceIn(0L, MAX_DURATION_MILLIS)
        return state.copy(
            totalMillis = safe,
            remainingMillis = safe,
            endAtElapsed = 0L,
            phase = if (safe > 0L) Phase.IDLE else Phase.IDLE
        )
    }

    /** Adds (or with a negative value removes) time; works while running too. */
    fun adjust(state: State, deltaMillis: Long, nowElapsed: Long): State {
        val left = state.remaining(nowElapsed)
        val newLeft = (left + deltaMillis).coerceIn(0L, MAX_DURATION_MILLIS)
        val newTotal = maxOf(state.totalMillis + deltaMillis, newLeft).coerceAtMost(
            MAX_DURATION_MILLIS
        )
        return if (state.phase == Phase.RUNNING) {
            state.copy(
                totalMillis = newTotal,
                endAtElapsed = nowElapsed + newLeft,
                remainingMillis = newLeft
            )
        } else {
            state.copy(
                totalMillis = newTotal,
                remainingMillis = newLeft,
                phase = if (newLeft == 0L) Phase.IDLE else state.phase
            )
        }
    }

    fun start(state: State, nowElapsed: Long): State {
        if (state.remainingMillis <= 0L) return state
        return state.copy(
            endAtElapsed = nowElapsed + state.remainingMillis,
            phase = Phase.RUNNING
        )
    }

    fun pause(state: State, nowElapsed: Long): State {
        if (state.phase != Phase.RUNNING) return state
        return state.copy(
            remainingMillis = state.remaining(nowElapsed),
            phase = Phase.PAUSED
        )
    }

    fun reset(state: State): State = State(
        totalMillis = state.totalMillis,
        remainingMillis = state.totalMillis,
        endAtElapsed = 0L,
        phase = Phase.IDLE
    )

    /**
     * Advances the machine. Returns a [Phase.FINISHED] state exactly once the deadline passes;
     * the caller fires the alert on the IDLE/RUNNING -> FINISHED edge.
     */
    fun tick(state: State, nowElapsed: Long): State {
        if (state.phase != Phase.RUNNING) return state
        return if (nowElapsed >= state.endAtElapsed) {
            state.copy(remainingMillis = 0L, phase = Phase.FINISHED)
        } else {
            state.copy(remainingMillis = state.endAtElapsed - nowElapsed)
        }
    }

    fun dismiss(state: State): State =
        if (state.phase == Phase.FINISHED) reset(state) else state

    /** 24 hours; longer belongs in an alarm, not a timer. */
    const val MAX_DURATION_MILLIS = 24L * 60 * 60 * 1000
}

/**
 * Stopwatch state machine with laps. Same design rules as [TimerEngine]: pure, monotonic-clock
 * driven, and safe to persist as plain data.
 */
object StopwatchEngine {

    /** @param splitMillis time since the previous lap; @param totalMillis time since start. */
    data class Lap(val index: Int, val splitMillis: Long, val totalMillis: Long)

    data class State(
        /** Monotonic timestamp of the last resume, 0 when not running. */
        val startedAtElapsed: Long = 0L,
        /** Time banked by previous run segments. */
        val accumulatedMillis: Long = 0L,
        val running: Boolean = false,
        val laps: List<Lap> = emptyList()
    ) {
        val started: Boolean get() = running || accumulatedMillis > 0L

        fun elapsed(nowElapsed: Long): Long =
            if (running) accumulatedMillis + (nowElapsed - startedAtElapsed).coerceAtLeast(0L)
            else accumulatedMillis
    }

    fun start(state: State, nowElapsed: Long): State =
        if (state.running) state
        else state.copy(startedAtElapsed = nowElapsed, running = true)

    fun pause(state: State, nowElapsed: Long): State =
        if (!state.running) state
        else state.copy(
            accumulatedMillis = state.elapsed(nowElapsed),
            startedAtElapsed = 0L,
            running = false
        )

    fun toggle(state: State, nowElapsed: Long): State =
        if (state.running) pause(state, nowElapsed) else start(state, nowElapsed)

    fun reset(): State = State()

    /** Records a lap. Ignored when the stopwatch has never been started. */
    fun lap(state: State, nowElapsed: Long): State {
        if (!state.started) return state
        val total = state.elapsed(nowElapsed)
        val previousTotal = state.laps.firstOrNull()?.totalMillis ?: 0L
        val lap = Lap(
            index = state.laps.size + 1,
            splitMillis = (total - previousTotal).coerceAtLeast(0L),
            totalMillis = total
        )
        // Newest first: the list is rendered top-down and the user watches the latest lap.
        return state.copy(laps = listOf(lap) + state.laps)
    }

    /** Index of the fastest lap, or null with fewer than two laps (nothing to compare yet). */
    fun fastestLapIndex(laps: List<Lap>): Int? =
        if (laps.size < 2) null else laps.minByOrNull { it.splitMillis }?.index

    /** Index of the slowest lap, or null with fewer than two laps. */
    fun slowestLapIndex(laps: List<Lap>): Int? =
        if (laps.size < 2) null else laps.maxByOrNull { it.splitMillis }?.index
}

/** Formatting shared by the timer and the stopwatch, kept pure so it is testable. */
object DurationFormatter {

    /** `H:MM:SS` when hours are present, otherwise `M:SS`. Used by the countdown timer. */
    fun formatClock(millis: Long): String {
        val total = (millis.coerceAtLeast(0L) + 999) / 1000      // round up: 0.4s left shows "1"
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** `MM:SS.cc` (or `H:MM:SS.cc`) with centiseconds. Used by the stopwatch. */
    fun formatPrecise(millis: Long): String {
        val safe = millis.coerceAtLeast(0L)
        val hours = safe / 3_600_000
        val minutes = (safe % 3_600_000) / 60_000
        val seconds = (safe % 60_000) / 1000
        val centis = (safe % 1000) / 10
        return if (hours > 0) {
            "%d:%02d:%02d.%02d".format(hours, minutes, seconds, centis)
        } else {
            "%02d:%02d.%02d".format(minutes, seconds, centis)
        }
    }
}
