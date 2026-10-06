package com.digitalclockpro.clockengine

import com.digitalclockpro.clockengine.ChessClockEngine.Phase
import com.digitalclockpro.clockengine.ChessClockEngine.Player
import com.digitalclockpro.clockengine.ChessClockEngine.TimeControl
import com.digitalclockpro.clockengine.ChessClockEngine.TimeControlType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ChessClockEngineTest {

    private val suddenDeath = TimeControl(TimeControlType.SUDDEN_DEATH, baseMillis = 60_000L)
    private val fischer =
        TimeControl(TimeControlType.FISCHER_INCREMENT, baseMillis = 60_000L, bonusMillis = 2_000L)
    private val simpleDelay =
        TimeControl(TimeControlType.SIMPLE_DELAY, baseMillis = 60_000L, bonusMillis = 3_000L)
    private val bronstein =
        TimeControl(TimeControlType.BRONSTEIN, baseMillis = 60_000L, bonusMillis = 3_000L)

    private fun started(control: TimeControl, at: Long = 0L) =
        ChessClockEngine.start(ChessClockEngine.reset(control), Player.A, at)

    private fun remaining(s: ChessClockEngine.State, p: Player, now: Long) =
        ChessClockEngine.remaining(s, p, now)

    // ---------------------------------------------------------------- setup

    @Test
    fun `a fresh game gives both players the full allowance and nobody the move`() {
        val state = ChessClockEngine.reset(suddenDeath)
        assertEquals(Phase.IDLE, state.phase)
        assertNull(state.active)
        assertNull(state.flagged)
        assertEquals(60_000L, state.a.storedMillis)
        assertEquals(60_000L, state.b.storedMillis)
        assertEquals(0, state.totalMoves)
    }

    @Test
    fun `starting puts the chosen player on move`() {
        val state = started(suddenDeath)
        assertEquals(Phase.RUNNING, state.phase)
        assertEquals(Player.A, state.active)
    }

    @Test
    fun `starting an already running game is ignored`() {
        val running = started(suddenDeath, at = 0L)
        val again = ChessClockEngine.start(running, Player.B, 5_000L)
        assertSame(running, again)
        assertEquals(Player.A, again.active)
    }

    @Test
    fun `an absurd time control is clamped rather than accepted`() {
        val state = ChessClockEngine.reset(TimeControl(TimeControlType.SUDDEN_DEATH, -5_000L))
        assertTrue(state.a.storedMillis >= 1_000L)
    }

    // ---------------------------------------------------------------- only one clock runs

    @Test
    fun `only the player on move loses time`() {
        val state = started(suddenDeath)
        assertEquals(55_000L, remaining(state, Player.A, 5_000L))
        assertEquals(60_000L, remaining(state, Player.B, 5_000L))
    }

    @Test
    fun `pressing hands the clock to the opponent`() {
        val state = ChessClockEngine.press(started(suddenDeath), Player.A, 5_000L)
        assertEquals(Player.B, state.active)
        assertEquals(55_000L, remaining(state, Player.A, 9_000L))   // frozen
        assertEquals(56_000L, remaining(state, Player.B, 9_000L))   // running since 5s
    }

    @Test
    fun `pressing out of turn does nothing`() {
        val state = started(suddenDeath)
        val ignored = ChessClockEngine.press(state, Player.B, 5_000L)
        assertSame(state, ignored)
        assertEquals(Player.A, ignored.active)
    }

    @Test
    fun `pressing before the game starts does nothing`() {
        val idle = ChessClockEngine.reset(suddenDeath)
        assertSame(idle, ChessClockEngine.press(idle, Player.A, 1_000L))
    }

    @Test
    fun `move counters advance per player`() {
        var s = started(suddenDeath)
        s = ChessClockEngine.press(s, Player.A, 1_000L)
        s = ChessClockEngine.press(s, Player.B, 2_000L)
        s = ChessClockEngine.press(s, Player.A, 3_000L)
        assertEquals(2, s.a.moves)
        assertEquals(1, s.b.moves)
        assertEquals(3, s.totalMoves)
    }

    // ---------------------------------------------------------------- sudden death

    @Test
    fun `sudden death just counts down`() {
        var s = started(suddenDeath)
        s = ChessClockEngine.press(s, Player.A, 10_000L)
        assertEquals(50_000L, s.a.storedMillis)
    }

    @Test
    fun `running out of time ends the game and names the loser`() {
        val s = ChessClockEngine.tick(started(suddenDeath), 60_001L)
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(Player.A, s.flagged)
        assertEquals(0L, remaining(s, Player.A, 70_000L))
    }

    @Test
    fun `a clock never reads below zero`() {
        val s = started(suddenDeath)
        assertEquals(0L, remaining(s, Player.A, 999_999L))
    }

    @Test
    fun `pressing after the flag falls is ignored`() {
        val finished = ChessClockEngine.tick(started(suddenDeath), 60_001L)
        assertSame(finished, ChessClockEngine.press(finished, Player.A, 60_002L))
    }

    @Test
    fun `flagging on your own move earns no increment`() {
        val s = ChessClockEngine.press(started(fischer), Player.A, 61_000L)
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(Player.A, s.flagged)
        assertEquals(0L, s.a.storedMillis)
        assertEquals(0, s.a.moves)      // the move was never completed
    }

    // ---------------------------------------------------------------- Fischer increment

    @Test
    fun `fischer credits the bonus after a completed move`() {
        val s = ChessClockEngine.press(started(fischer), Player.A, 5_000L)
        // used 5s, refunded 2s
        assertEquals(57_000L, s.a.storedMillis)
    }

    @Test
    fun `fischer bonuses accumulate when you move fast`() {
        var s = started(fischer)
        var now = 0L
        repeat(5) {
            now += 500L                                   // half a second per move
            s = ChessClockEngine.press(s, Player.A, now)
            now += 500L
            s = ChessClockEngine.press(s, Player.B, now)
        }
        // 5 moves x (2000 gained - 500 spent) = +7500
        assertEquals(67_500L, s.a.storedMillis)
        assertEquals(67_500L, s.b.storedMillis)
    }

    @Test
    fun `fischer counts down from the first millisecond`() {
        val s = started(fischer)
        assertEquals(59_000L, remaining(s, Player.A, 1_000L))
    }

    // ---------------------------------------------------------------- simple delay

    @Test
    fun `simple delay freezes the clock for the delay period`() {
        val s = started(simpleDelay)
        assertEquals(60_000L, remaining(s, Player.A, 1_000L))
        assertEquals(60_000L, remaining(s, Player.A, 3_000L))
    }

    @Test
    fun `simple delay charges only the time beyond the delay`() {
        val s = started(simpleDelay)
        assertEquals(59_000L, remaining(s, Player.A, 4_000L))
        val pressed = ChessClockEngine.press(s, Player.A, 4_000L)
        assertEquals(59_000L, pressed.a.storedMillis)
    }

    @Test
    fun `moving inside the delay costs nothing at all`() {
        val s = ChessClockEngine.press(started(simpleDelay), Player.A, 2_500L)
        assertEquals(60_000L, s.a.storedMillis)
    }

    @Test
    fun `simple delay never adds time`() {
        var s = started(simpleDelay)
        repeat(3) { i -> s = ChessClockEngine.press(s, if (i % 2 == 0) Player.A else Player.B, (i + 1) * 1_000L) }
        assertTrue(s.a.storedMillis <= 60_000L)
        assertTrue(s.b.storedMillis <= 60_000L)
    }

    // ---------------------------------------------------------------- Bronstein

    @Test
    fun `bronstein counts down during the move unlike simple delay`() {
        val s = started(bronstein)
        // This is the whole difference from SIMPLE_DELAY: the display drops immediately.
        assertEquals(59_000L, remaining(s, Player.A, 1_000L))
    }

    @Test
    fun `bronstein refunds exactly what was used when inside the delay`() {
        val s = ChessClockEngine.press(started(bronstein), Player.A, 2_000L)
        assertEquals(60_000L, s.a.storedMillis)
    }

    @Test
    fun `bronstein caps the refund at the delay`() {
        val s = ChessClockEngine.press(started(bronstein), Player.A, 10_000L)
        // used 10s, refunded only the 3s delay
        assertEquals(53_000L, s.a.storedMillis)
    }

    @Test
    fun `bronstein and simple delay leave the same time after a slow move`() {
        val viaBronstein = ChessClockEngine.press(started(bronstein), Player.A, 8_000L)
        val viaSimple = ChessClockEngine.press(started(simpleDelay), Player.A, 8_000L)
        assertEquals(viaSimple.a.storedMillis, viaBronstein.a.storedMillis)
    }

    // ---------------------------------------------------------------- pause

    @Test
    fun `pausing banks the time used so far`() {
        val paused = ChessClockEngine.pause(started(suddenDeath), 5_000L)
        assertEquals(Phase.PAUSED, paused.phase)
        assertEquals(55_000L, paused.a.storedMillis)
        // Time passing while paused costs nothing.
        assertEquals(55_000L, remaining(paused, Player.A, 90_000L))
    }

    @Test
    fun `resuming continues from where it stopped`() {
        val paused = ChessClockEngine.pause(started(suddenDeath), 5_000L)
        val resumed = ChessClockEngine.resume(paused, 100_000L)
        assertEquals(Phase.RUNNING, resumed.phase)
        assertEquals(53_000L, remaining(resumed, Player.A, 102_000L))
    }

    @Test
    fun `resuming an idle game is ignored`() {
        val idle = ChessClockEngine.reset(suddenDeath)
        assertSame(idle, ChessClockEngine.resume(idle, 1_000L))
    }

    // ---------------------------------------------------------------- misc

    @Test
    fun `the critical flag trips only inside the last ten seconds`() {
        val s = started(suddenDeath)
        assertFalse(ChessClockEngine.isCritical(s, Player.A, 49_000L))
        assertTrue(ChessClockEngine.isCritical(s, Player.A, 50_000L))
    }

    @Test
    fun `an idle game is never critical`() {
        assertFalse(ChessClockEngine.isCritical(ChessClockEngine.reset(suddenDeath), Player.A, 0L))
    }

    @Test
    fun `changing the control restarts the game`() {
        var s = started(suddenDeath)
        s = ChessClockEngine.press(s, Player.A, 10_000L)
        val switched = ChessClockEngine.setControl(s, fischer)
        assertEquals(Phase.IDLE, switched.phase)
        assertEquals(0, switched.totalMoves)
        assertEquals(60_000L, switched.a.storedMillis)
    }

    @Test
    fun `every preset produces a usable game`() {
        ChessClockEngine.Preset.entries.forEach { preset ->
            val s = ChessClockEngine.reset(preset.control)
            assertTrue(preset.name, s.a.storedMillis > 0L)
            assertEquals(preset.name, s.a.storedMillis, s.b.storedMillis)
        }
    }

    @Test
    fun `shorthand reads the way players write time controls`() {
        assertEquals("3+2", ChessClockEngine.shorthand(ChessClockEngine.Preset.BLITZ_3_2.control))
        assertEquals("1+0", ChessClockEngine.shorthand(ChessClockEngine.Preset.BULLET_1_0.control))
        assertEquals("15+10", ChessClockEngine.shorthand(ChessClockEngine.Preset.RAPID_15_10.control))
    }

    @Test
    fun `a full fast game keeps both clocks consistent`() {
        var s = started(fischer)
        var now = 0L
        var side = Player.A
        repeat(40) {
            now += 1_200L
            s = ChessClockEngine.press(s, side, now)
            side = side.opponent
        }
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(40, s.totalMoves)
        // 20 moves each, 1.2s spent and 2s gained per move -> +0.8s per move.
        assertEquals(76_000L, s.a.storedMillis)
        assertEquals(76_000L, s.b.storedMillis)
    }

    // ------------------------------------------------- thinking-time tally

    @Test
    fun `thinking time is tallied per player as turns are settled`() {
        var s = started(suddenDeath, at = 0L)            // A on move
        s = ChessClockEngine.press(s, Player.A, 5_000L)  // A thought 5s
        s = ChessClockEngine.press(s, Player.B, 12_000L) // B thought 7s
        assertEquals(5_000L, s.a.spentMillis)
        assertEquals(7_000L, s.b.spentMillis)
    }

    @Test
    fun `paused time is never charged to anybody`() {
        var s = started(suddenDeath, at = 0L)            // A on move
        s = ChessClockEngine.press(s, Player.A, 5_000L)  // B on move now
        s = ChessClockEngine.pause(s, 8_000L)            // B charged the 3s so far
        s = ChessClockEngine.resume(s, 60_000L)          // 52s of paused silence
        s = ChessClockEngine.press(s, Player.B, 61_000L) // B presses 1s after resume
        assertEquals(0L, s.a.spentMillis)
        assertEquals(3_000L + 1_000L, s.b.spentMillis)   // only real thinking counts
    }

    @Test
    fun `a simple delay is free thinking time in the tally too`() {
        var s = started(simpleDelay, at = 0L)            // A on move, 3s delay
        s = ChessClockEngine.press(s, Player.A, 2_000L)  // pressed inside the delay window
        assertEquals(0L, s.a.spentMillis)
        s = ChessClockEngine.press(s, Player.B, 8_000L)  // B used 6s of which 3 were free
        assertEquals(3_000L, s.b.spentMillis)
    }

    // ------------------------------------------------- early finish (resign)

    @Test
    fun `finishing early is a resignation by the player on move`() {
        var s = started(suddenDeath, at = 0L)            // A on move
        s = ChessClockEngine.press(s, Player.A, 5_000L)   // B on move
        val done = ChessClockEngine.finish(s, 12_000L)    // B concedes after 7s
        assertEquals(Phase.FINISHED, done.phase)
        assertEquals(Player.B, done.flagged)
        assertEquals(ChessClockEngine.EndReason.RESIGN, done.endedBy)
        assertEquals(5_000L, done.a.spentMillis)
        assertEquals(7_000L, done.b.spentMillis)
    }

    @Test
    fun `finish is ignored unless a game is actually in progress`() {
        val idle = ChessClockEngine.reset(suddenDeath)
        assertSame(idle, ChessClockEngine.finish(idle, 100L))
        val finished = ChessClockEngine.finish(started(suddenDeath), 5_000L)
        assertSame(finished, ChessClockEngine.finish(finished, 6_000L))
    }

    @Test
    fun `finishing at the exact moment the clock hits zero is a loss on time`() {
        var s = started(suddenDeath, at = 0L)
        s = ChessClockEngine.press(s, Player.A, 5_000L)   // B on move with 60s
        val done = ChessClockEngine.finish(s, 65_000L)    // B's clock empties right now
        assertEquals(Phase.FINISHED, done.phase)
        assertEquals(ChessClockEngine.EndReason.TIME_OUT, done.endedBy)
    }

    // ------------------------------------------------- the flag-fall summary

    @Test
    fun `a flag fall ends the game as a loss on time and settles the final charge`() {
        var s = started(suddenDeath, at = 0L)
        s = ChessClockEngine.press(s, Player.A, 5_000L)   // B on move with 60s
        val done = ChessClockEngine.tick(s, 65_000L)      // B burns the whole clock
        assertEquals(Phase.FINISHED, done.phase)
        assertEquals(Player.B, done.flagged)
        assertEquals(ChessClockEngine.EndReason.TIME_OUT, done.endedBy)
        assertEquals(60_000L, done.b.spentMillis)
    }

    @Test
    fun `the summary describes the finished game and nothing else`() {
        assertNull(ChessClockEngine.summarize(started(suddenDeath)))

        var s = started(suddenDeath, at = 0L)
        s = ChessClockEngine.press(s, Player.A, 5_000L)   // A: 1 move, 5s, 55s left
        val done = ChessClockEngine.finish(s, 12_000L)    // B resigns after 7s

        val summary = ChessClockEngine.summarize(done)!!
        assertEquals(Player.A, summary.winner)
        assertEquals(Player.B, summary.loser)
        assertEquals(ChessClockEngine.EndReason.RESIGN, summary.endedBy)
        assertEquals(1, summary.a.moves)
        assertEquals(0, summary.b.moves)
        assertEquals(55_000L, summary.a.remainingMillis)
        assertEquals(53_000L, summary.b.remainingMillis)
        assertEquals(12_000L, summary.activeMillis)        // 5s + 7s of real play
        assertEquals(5_000L, summary.a.averageMillisPerMove)
        assertNull(summary.b.averageMillisPerMove)         // no completed move
    }
}
