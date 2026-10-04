package com.digitalclockpro.clockengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DayNightTest {

    private val marchEquinox = 80      // ~20 March
    private val juneSolstice = 172     // ~21 June
    private val decemberSolstice = 355 // ~21 December

    // ---------------------------------------------------------------- sun times

    @Test
    fun `at the equator the sun rises near six all year`() {
        listOf(marchEquinox, juneSolstice, decemberSolstice).forEach { day ->
            val sun = DayNight.sunTimes(latitude = 0.0, dayOfYear = day)
            assertTrue(
                "day $day sunrise was ${sun.sunriseMinute}",
                abs(sun.sunriseMinute - 6 * 60) <= 15
            )
            assertTrue(abs(sun.sunsetMinute - 18 * 60) <= 15)
        }
    }

    @Test
    fun `at the equinox every latitude has a twelve hour day`() {
        listOf(-40.0, 0.0, 35.0, 60.0).forEach { latitude ->
            val sun = DayNight.sunTimes(latitude, marchEquinox)
            val length = sun.sunsetMinute - sun.sunriseMinute
            assertTrue("latitude $latitude gave $length minutes", abs(length - 12 * 60) <= 30)
        }
    }

    @Test
    fun `northern summer days are long and winter days are short`() {
        val summer = DayNight.sunTimes(latitude = 51.5, dayOfYear = juneSolstice)
        val winter = DayNight.sunTimes(latitude = 51.5, dayOfYear = decemberSolstice)
        val summerLength = summer.sunsetMinute - summer.sunriseMinute
        val winterLength = winter.sunsetMinute - winter.sunriseMinute
        assertTrue("summer=$summerLength winter=$winterLength", summerLength > winterLength)
        assertTrue(summerLength > 14 * 60)
        assertTrue(winterLength < 10 * 60)
    }

    @Test
    fun `the southern hemisphere is inverted`() {
        val sydneyJune = DayNight.sunTimes(latitude = -33.9, dayOfYear = juneSolstice)
        val sydneyDecember = DayNight.sunTimes(latitude = -33.9, dayOfYear = decemberSolstice)
        assertTrue(
            (sydneyDecember.sunsetMinute - sydneyDecember.sunriseMinute) >
                (sydneyJune.sunsetMinute - sydneyJune.sunriseMinute)
        )
    }

    @Test
    fun `midnight sun above the arctic circle`() {
        val sun = DayNight.sunTimes(latitude = 78.0, dayOfYear = juneSolstice)
        assertTrue(sun.polarDay)
        assertFalse(sun.polarNight)
        assertTrue(DayNight.isDaytime(minuteOfDay = 2 * 60, sun = sun))
        assertEquals(DayNight.Phase.DAY, DayNight.phase(2 * 60, sun))
        assertEquals(1f, DayNight.daylightFraction(2 * 60, sun), 0.0001f)
    }

    @Test
    fun `polar night above the arctic circle`() {
        val sun = DayNight.sunTimes(latitude = 78.0, dayOfYear = decemberSolstice)
        assertTrue(sun.polarNight)
        assertFalse(DayNight.isDaytime(minuteOfDay = 12 * 60, sun = sun))
        assertEquals(DayNight.Phase.NIGHT, DayNight.phase(12 * 60, sun))
        assertEquals(0f, DayNight.daylightFraction(12 * 60, sun), 0.0001f)
    }

    @Test
    fun `out of range day numbers are clamped instead of throwing`() {
        val low = DayNight.sunTimes(45.0, dayOfYear = -5)
        val high = DayNight.sunTimes(45.0, dayOfYear = 999)
        assertTrue(low.sunriseMinute in 0..1440)
        assertTrue(high.sunriseMinute in 0..1440)
    }

    // ---------------------------------------------------------------- phase

    private val sun = DayNight.SunTimes(sunriseMinute = 6 * 60, sunsetMinute = 18 * 60)

    @Test
    fun `phases follow the course of the day`() {
        assertEquals(DayNight.Phase.NIGHT, DayNight.phase(2 * 60, sun))
        assertEquals(DayNight.Phase.DAWN, DayNight.phase(5 * 60 + 30, sun))
        assertEquals(DayNight.Phase.SUNRISE, DayNight.phase(6 * 60, sun))
        assertEquals(DayNight.Phase.DAY, DayNight.phase(12 * 60, sun))
        assertEquals(DayNight.Phase.SUNSET, DayNight.phase(18 * 60, sun))
        assertEquals(DayNight.Phase.DUSK, DayNight.phase(18 * 60 + 30, sun))
        assertEquals(DayNight.Phase.NIGHT, DayNight.phase(23 * 60, sun))
    }

    @Test
    fun `daytime boundaries are inclusive at sunrise and exclusive at sunset`() {
        assertTrue(DayNight.isDaytime(6 * 60, sun))
        assertTrue(DayNight.isDaytime(17 * 60 + 59, sun))
        assertFalse(DayNight.isDaytime(18 * 60, sun))
        assertFalse(DayNight.isDaytime(5 * 60 + 59, sun))
    }

    // ---------------------------------------------------------------- daylight fraction

    @Test
    fun `daylight peaks at solar noon and is zero at night`() {
        assertEquals(0f, DayNight.daylightFraction(3 * 60, sun), 0.0001f)
        assertEquals(1f, DayNight.daylightFraction(12 * 60, sun), 0.01f)
        assertEquals(0f, DayNight.daylightFraction(6 * 60, sun), 0.01f)
        assertEquals(0f, DayNight.daylightFraction(18 * 60, sun), 0.01f)
    }

    @Test
    fun `daylight fraction is monotonic through the morning`() {
        var previous = -1f
        for (hour in 6..12) {
            val value = DayNight.daylightFraction(hour * 60, sun)
            assertTrue("hour $hour dipped", value >= previous)
            previous = value
        }
    }

    @Test
    fun `daylight fraction always stays inside zero and one`() {
        for (minute in 0..1440 step 7) {
            val value = DayNight.daylightFraction(minute, sun)
            assertTrue(value in 0f..1f)
        }
    }

    // ---------------------------------------------------------------- elevation

    @Test
    fun `the sun is overhead at the equator at noon on the equinox`() {
        val elevation = DayNight.solarElevationDegrees(0.0, marchEquinox, 12 * 60)
        assertTrue("elevation was $elevation", elevation > 85.0)
    }

    @Test
    fun `the sun is below the horizon at midnight in the tropics`() {
        val elevation = DayNight.solarElevationDegrees(25.0, juneSolstice, 0)
        assertTrue("elevation was $elevation", elevation < 0.0)
    }
}
