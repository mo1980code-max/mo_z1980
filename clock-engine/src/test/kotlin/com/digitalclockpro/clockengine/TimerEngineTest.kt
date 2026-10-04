package com.digitalclockpro.clockengine

import com.digitalclockpro.clockengine.TimerEngine.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimerEngineTest {

    private val oneMinute = 60_000L

    @Test
    fun `setting a duration makes the timer startable`() {
        val s = TimerEngine.setDuration(TimerEngine.State(), oneMinute)
        assertEquals(oneMinute, s.totalMillis)
        assertEquals(oneMinute, s.remainingMillis)
        assertEquals(Phase.IDLE, s.phase)
        assertTrue(s.canStart)
    }

    @Test
    fun `a zero duration cannot be started`() {
        assertFalse(TimerEngine.setDuration(TimerEngine.State(), 0L).canStart)
    }

    @Test
    fun `durations are capped at twenty four hours`() {
        val s = TimerEngine.setDuration(TimerEngine.State(), TimerEngine.MAX_DURATION_MILLIS * 3)
        assertEquals(TimerEngine.MAX_DURATION_MILLIS, s.totalMillis)
    }

    @Test
    fun `running timer counts down against the monotonic clock`() {
        var s = TimerEngine.setDuration(TimerEngine.State(), oneMinute)
        s = TimerEngine.start(s, nowElapsed = 10_000L)
        assertEquals(Phase.RUNNING, s.phase)
        assertEquals(oneMinute, s.remaining(10_000L))
        assertEquals(40_000L, s.remaining(30_000L))
        assertEquals(0L, s.remaining(999_999L))        // never negative
    }

    @Test
    fun `tick finishes the timer exactly at the deadline`() {
        var s = TimerEngine.start(TimerEngine.setDuration(TimerEngine.State(), 5_000L), 0L)
        s = TimerEngine.tick(s, 4_999L)
        assertEquals(Phase.RUNNING, s.phase)
        s = TimerEngine.tick(s, 5_000L)
        assertEquals(Phase.FINISHED, s.phase)
        assertEquals(0L, s.remainingMillis)
    }

    @Test
    fun `pause banks the remaining time and resume continues from it`() {
        var s = TimerEngine.start(TimerEngine.setDuration(TimerEngine.State(), oneMinute), 0L)
        s = TimerEngine.pause(s, 20_000L)
        assertEquals(Phase.PAUSED, s.phase)
        assertEquals(40_000L, s.remainingMillis)

        // Resuming much later must not lose the paused time.
        s = TimerEngine.start(s, 500_000L)
        assertEquals(40_000L, s.remaining(500_000L))
    }

    @Test
    fun `pausing a timer that is not running is a no-op`() {
        val idle = TimerEngine.setDuration(TimerEngine.State(), oneMinute)
        assertEquals(idle, TimerEngine.pause(idle, 1_000L))
    }

    @Test
    fun `reset restores the full duration`() {
        var s = TimerEngine.start(TimerEngine.setDuration(TimerEngine.State(), oneMinute), 0L)
        s = TimerEngine.tick(s, oneMinute)
        assertEquals(Phase.FINISHED, s.phase)
        s = TimerEngine.reset(s)
        assertEquals(Phase.IDLE, s.phase)
        assertEquals(oneMinute, s.remainingMillis)
    }

    @Test
    fun `adjusting while running shifts the deadline`() {
        var s = TimerEngine.start(TimerEngine.setDuration(TimerEngine.State(), oneMinute), 0L)
        s = TimerEngine.adjust(s, 30_000L, nowElapsed = 10_000L)
        // 50s were left, +30s = 80s from now.
        assertEquals(80_000L, s.remaining(10_000L))
        assertEquals(Phase.RUNNING, s.phase)
    }

    @Test
    fun `adjusting cannot drive the remaining time below zero`() {
        var s = TimerEngine.start(TimerEngine.setDuration(TimerEngine.State(), 10_000L), 0L)
        s = TimerEngine.adjust(s, -60_000L, nowElapsed = 0L)
        assertEquals(0L, s.remaining(0L))
    }

    @Test
    fun `progress runs from zero to one`() {
        val s = TimerEngine.start(TimerEngine.setDuration(TimerEngine.State(), 100_000L), 0L)
        assertEquals(0.0, s.progress(0L).toDouble(), 0.001)
        assertEquals(0.5, s.progress(50_000L).toDouble(), 0.001)
        assertEquals(1.0, s.progress(100_000L).toDouble(), 0.001)
        assertEquals(1.0, s.progress(900_000L).toDouble(), 0.001)
    }

    @Test
    fun `dismiss only acts on a finished timer`() {
        val running = TimerEngine.start(TimerEngine.setDuration(TimerEngine.State(), oneMinute), 0L)
        assertEquals(running, TimerEngine.dismiss(running))

        val finished = TimerEngine.tick(running, oneMinute)
        assertEquals(Phase.IDLE, TimerEngine.dismiss(finished).phase)
    }
}
