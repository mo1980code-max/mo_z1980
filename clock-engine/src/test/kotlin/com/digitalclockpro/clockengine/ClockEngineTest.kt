package com.digitalclockpro.clockengine

import com.digitalclockpro.clockengine.ClockEngine.HandMotion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ClockEngineTest {

    private fun assertDeg(expected: Float, actual: Float) =
        assertEquals(expected.toDouble(), actual.toDouble(), 0.001)

    // ---------------------------------------------------------------- hand angles

    @Test
    fun `midnight puts every hand at twelve`() {
        val a = ClockEngine.handAngles(0, 0, 0, motion = HandMotion.QUARTZ)
        assertDeg(0f, a.hourDegrees)
        assertDeg(0f, a.minuteDegrees)
        assertDeg(0f, a.secondDegrees)
    }

    @Test
    fun `three o'clock points the hour hand at ninety degrees`() {
        assertDeg(90f, ClockEngine.handAngles(3, 0, 0).hourDegrees)
        assertDeg(180f, ClockEngine.handAngles(6, 0, 0).hourDegrees)
        assertDeg(270f, ClockEngine.handAngles(9, 0, 0).hourDegrees)
    }

    @Test
    fun `afternoon hours wrap onto the twelve-hour dial`() {
        assertDeg(
            ClockEngine.handAngles(3, 0, 0).hourDegrees,
            ClockEngine.handAngles(15, 0, 0).hourDegrees
        )
        assertDeg(0f, ClockEngine.handAngles(12, 0, 0).hourDegrees)
    }

    @Test
    fun `hour hand creeps with the minutes even in quartz mode`() {
        // Half past three: the hour hand must sit halfway between 3 and 4, not on 3.
        assertDeg(105f, ClockEngine.handAngles(3, 30, 0, motion = HandMotion.QUARTZ).hourDegrees)
    }

    @Test
    fun `quartz snaps seconds and minutes to whole steps`() {
        val a = ClockEngine.handAngles(1, 30, 45, nanosecond = 900_000_000, motion = HandMotion.QUARTZ)
        assertDeg(270f, a.secondDegrees)        // 45 * 6, nanoseconds ignored
        assertDeg(180f, a.minuteDegrees)        // 30 * 6, no sub-minute creep
    }

    @Test
    fun `smooth interpolates sub-second movement`() {
        val a = ClockEngine.handAngles(1, 30, 45, nanosecond = 500_000_000, motion = HandMotion.SMOOTH)
        assertDeg(273f, a.secondDegrees)        // 45.5 * 6
        assertTrue(a.minuteDegrees > 180f)      // minute hand has crept past the half mark
    }

    @Test
    fun `smooth and quartz agree exactly on whole seconds`() {
        val quartz = ClockEngine.handAngles(8, 20, 10, 0, HandMotion.QUARTZ)
        val smooth = ClockEngine.handAngles(8, 20, 10, 0, HandMotion.SMOOTH)
        assertDeg(quartz.secondDegrees, smooth.secondDegrees)
        // The minute hands differ: quartz steps, smooth creeps.
        assertNotEquals(quartz.minuteDegrees, smooth.minuteDegrees)
    }

    @Test
    fun `out of range values are clamped instead of throwing`() {
        val a = ClockEngine.handAngles(-5, 90, 90)
        assertTrue(a.hourDegrees in 0f..360f)
        assertDeg(354f, a.minuteDegrees)        // clamped to 59
        assertDeg(354f, a.secondDegrees)
    }

    @Test
    fun `smooth refreshes far more often than quartz`() {
        assertEquals(1_000L, ClockEngine.frameIntervalMillis(HandMotion.QUARTZ))
        assertEquals(16L, ClockEngine.frameIntervalMillis(HandMotion.SMOOTH))
    }

    // ---------------------------------------------------------------- dial geometry

    @Test
    fun `zero degrees maps to the top of the dial`() {
        val p = ClockEngine.pointOnDial(100f, 100f, 50f, 0f)
        assertEquals(100.0, p.x.toDouble(), 0.01)
        assertEquals(50.0, p.y.toDouble(), 0.01)     // screen Y grows downwards
    }

    @Test
    fun `ninety degrees maps to the right of the dial`() {
        val p = ClockEngine.pointOnDial(100f, 100f, 50f, 90f)
        assertEquals(150.0, p.x.toDouble(), 0.01)
        assertEquals(100.0, p.y.toDouble(), 0.01)
    }

    @Test
    fun `one hundred eighty degrees maps to the bottom`() {
        val p = ClockEngine.pointOnDial(0f, 0f, 10f, 180f)
        assertEquals(0.0, p.x.toDouble(), 0.01)
        assertEquals(10.0, p.y.toDouble(), 0.01)
    }

    @Test
    fun `tick angles divide the dial evenly`() {
        assertDeg(0f, ClockEngine.tickAngle(0))
        assertDeg(6f, ClockEngine.tickAngle(1))
        assertDeg(354f, ClockEngine.tickAngle(59))
        assertDeg(30f, ClockEngine.tickAngle(1, totalTicks = 12))
    }

    @Test
    fun `every fifth tick is major on a sixty tick dial`() {
        assertTrue(ClockEngine.isMajorTick(0))
        assertTrue(ClockEngine.isMajorTick(5))
        assertTrue(ClockEngine.isMajorTick(55))
        assertFalse(ClockEngine.isMajorTick(1))
        assertFalse(ClockEngine.isMajorTick(7))
    }

    @Test
    fun `tick zero is numbered twelve not zero`() {
        assertEquals(12, ClockEngine.numeralForTick(0))
        assertEquals(1, ClockEngine.numeralForTick(5))
        assertEquals(11, ClockEngine.numeralForTick(55))
    }

    @Test
    fun `roman numerals cover the whole dial`() {
        assertEquals("XII", ClockEngine.romanNumeral(12))
        assertEquals("XII", ClockEngine.romanNumeral(0))
        assertEquals("IV", ClockEngine.romanNumeral(4))
        assertEquals("IX", ClockEngine.romanNumeral(9))
        assertEquals("III", ClockEngine.romanNumeral(15))   // wraps
    }

    // ---------------------------------------------------------------- burn-in protection

    @Test
    fun `burn-in offset stays inside the configured amplitude`() {
        repeat(240) { step ->
            val offset = ClockEngine.burnInOffset(step * 1_000L, 24f, 12f)
            assertTrue(abs(offset.x) <= 24.001f)
            assertTrue(abs(offset.y) <= 12.001f)
        }
    }

    @Test
    fun `burn-in offset actually moves over time`() {
        val a = ClockEngine.burnInOffset(0L, 20f, 20f)
        val b = ClockEngine.burnInOffset(15_000L, 20f, 20f)
        assertTrue("the image must not stay on the same pixels", abs(a.x - b.x) > 1f)
    }

    @Test
    fun `burn-in path does not repeat after a single horizontal period`() {
        // The Y axis runs at double frequency, so half a period returns X but not the full point.
        val quarter = ClockEngine.burnInOffset(15_000L, 20f, 20f)
        val threeQuarter = ClockEngine.burnInOffset(45_000L, 20f, 20f)
        assertNotEquals(quarter.x, threeQuarter.x)
    }

    @Test
    fun `a zero period degrades to no movement instead of dividing by zero`() {
        val offset = ClockEngine.burnInOffset(1234L, 10f, 10f, periodMillis = 0L)
        assertDeg(0f, offset.x)
        assertDeg(0f, offset.y)
    }

    // ---------------------------------------------------------------- brightness

    @Test
    fun `dragging up brightens and dragging down dims`() {
        assertTrue(ClockEngine.brightnessAfterDrag(0.5f, -100f, 1000f) > 0.5f)
        assertTrue(ClockEngine.brightnessAfterDrag(0.5f, 100f, 1000f) < 0.5f)
    }

    @Test
    fun `brightness never reaches zero so the screen stays recoverable`() {
        val dimmed = ClockEngine.brightnessAfterDrag(0.1f, 10_000f, 1000f)
        assertEquals(ClockEngine.MIN_BRIGHTNESS.toDouble(), dimmed.toDouble(), 0.0001)
        assertTrue(dimmed > 0f)
    }

    @Test
    fun `brightness is capped at full`() {
        assertEquals(1.0, ClockEngine.brightnessAfterDrag(0.9f, -10_000f, 1000f).toDouble(), 0.0001)
    }

    @Test
    fun `a zero height screen cannot change brightness`() {
        assertEquals(0.4, ClockEngine.brightnessAfterDrag(0.4f, 500f, 0f).toDouble(), 0.0001)
    }

    @Test
    fun `brightness percent rounds for display`() {
        assertEquals(50, ClockEngine.brightnessPercent(0.5f))
        assertEquals(100, ClockEngine.brightnessPercent(1.5f))
        assertEquals(0, ClockEngine.brightnessPercent(-1f))
    }
}
