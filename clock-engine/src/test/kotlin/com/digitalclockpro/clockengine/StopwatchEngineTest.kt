package com.digitalclockpro.clockengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchEngineTest {

    @Test
    fun `a fresh stopwatch reads zero`() {
        val s = StopwatchEngine.State()
        assertEquals(0L, s.elapsed(10_000L))
        assertFalse(s.started)
    }

    @Test
    fun `elapsed grows while running`() {
        val s = StopwatchEngine.start(StopwatchEngine.State(), 1_000L)
        assertEquals(0L, s.elapsed(1_000L))
        assertEquals(4_000L, s.elapsed(5_000L))
        assertTrue(s.running)
    }

    @Test
    fun `pause freezes the reading and resume accumulates`() {
        var s = StopwatchEngine.start(StopwatchEngine.State(), 0L)
        s = StopwatchEngine.pause(s, 3_000L)
        assertFalse(s.running)
        assertEquals(3_000L, s.elapsed(9_999L))     // frozen regardless of "now"

        s = StopwatchEngine.start(s, 10_000L)
        assertEquals(5_000L, s.elapsed(12_000L))    // 3s banked + 2s new
    }

    @Test
    fun `toggle alternates between running and paused`() {
        var s = StopwatchEngine.toggle(StopwatchEngine.State(), 0L)
        assertTrue(s.running)
        s = StopwatchEngine.toggle(s, 1_000L)
        assertFalse(s.running)
    }

    @Test
    fun `reset clears laps and elapsed time`() {
        var s = StopwatchEngine.start(StopwatchEngine.State(), 0L)
        s = StopwatchEngine.lap(s, 1_000L)
        s = StopwatchEngine.reset()
        assertEquals(0L, s.elapsed(10_000L))
        assertTrue(s.laps.isEmpty())
    }

    @Test
    fun `laps record split and total time newest first`() {
        var s = StopwatchEngine.start(StopwatchEngine.State(), 0L)
        s = StopwatchEngine.lap(s, 5_000L)
        s = StopwatchEngine.lap(s, 8_000L)

        assertEquals(2, s.laps.size)
        val newest = s.laps.first()
        assertEquals(2, newest.index)
        assertEquals(3_000L, newest.splitMillis)
        assertEquals(8_000L, newest.totalMillis)

        val first = s.laps.last()
        assertEquals(1, first.index)
        assertEquals(5_000L, first.splitMillis)
    }

    @Test
    fun `lapping a stopwatch that never started is ignored`() {
        assertTrue(StopwatchEngine.lap(StopwatchEngine.State(), 1_000L).laps.isEmpty())
    }

    @Test
    fun `fastest and slowest laps need at least two laps`() {
        var s = StopwatchEngine.start(StopwatchEngine.State(), 0L)
        s = StopwatchEngine.lap(s, 5_000L)
        assertNull(StopwatchEngine.fastestLapIndex(s.laps))
        assertNull(StopwatchEngine.slowestLapIndex(s.laps))

        s = StopwatchEngine.lap(s, 6_000L)    // 1s split
        s = StopwatchEngine.lap(s, 15_000L)   // 9s split
        assertEquals(2, StopwatchEngine.fastestLapIndex(s.laps))
        assertEquals(3, StopwatchEngine.slowestLapIndex(s.laps))
    }
}

class DurationFormatterTest {

    @Test
    fun `countdown rounds up so the last second is visible`() {
        assertEquals("0:01", DurationFormatter.formatClock(1))
        assertEquals("0:01", DurationFormatter.formatClock(999))
        assertEquals("0:00", DurationFormatter.formatClock(0))
    }

    @Test
    fun `countdown shows hours only when needed`() {
        assertEquals("1:30", DurationFormatter.formatClock(90_000))
        assertEquals("59:00", DurationFormatter.formatClock(59 * 60_000L))
        assertEquals("1:00:00", DurationFormatter.formatClock(60 * 60_000L))
        assertEquals("2:05:03", DurationFormatter.formatClock((2 * 3600 + 5 * 60 + 3) * 1000L))
    }

    @Test
    fun `stopwatch shows centiseconds`() {
        assertEquals("00:00.00", DurationFormatter.formatPrecise(0))
        assertEquals("00:01.23", DurationFormatter.formatPrecise(1_230))
        assertEquals("01:00.00", DurationFormatter.formatPrecise(60_000))
        assertEquals("1:00:00.00", DurationFormatter.formatPrecise(3_600_000))
    }

    @Test
    fun `negative input is treated as zero`() {
        assertEquals("0:00", DurationFormatter.formatClock(-5_000))
        assertEquals("00:00.00", DurationFormatter.formatPrecise(-5_000))
    }
}
