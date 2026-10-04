package com.digitalclockpro.clockengine

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

/**
 * Offline sunrise / sunset estimation and the day-phase used to tint World Clock cards.
 *
 * No network and no Android location: sunrise is derived from the city's latitude and the day of
 * the year with the standard solar-declination approximation. It is accurate to a few minutes —
 * far more than enough to decide whether a card should look like dawn or midnight, and it keeps
 * the feature working fully offline.
 */
object DayNight {

    /** Coarse phases, ordered as the day runs. Drives the card gradient and the sun/moon icon. */
    enum class Phase { NIGHT, DAWN, SUNRISE, DAY, SUNSET, DUSK }

    /**
     * Sunrise/sunset in minutes after local midnight.
     *
     * Above the polar circles the sun may not rise or set at all, which is a real case for
     * Tromsø or Murmansk — the flags say so instead of returning a nonsense time.
     */
    data class SunTimes(
        val sunriseMinute: Int,
        val sunsetMinute: Int,
        val polarDay: Boolean = false,
        val polarNight: Boolean = false
    )

    private const val MINUTES_PER_DAY = 24 * 60

    /** Width of the dawn / dusk bands around sunrise and sunset. */
    const val TWILIGHT_MINUTES = 45

    /** Fallback used when a city has no stored coordinates (latitude 0 is also the equator). */
    val DEFAULT_SUN_TIMES = SunTimes(sunriseMinute = 6 * 60, sunsetMinute = 18 * 60)

    /**
     * Solar geometry for [latitude] on day [dayOfYear] (1..366).
     *
     * Longitude is deliberately ignored: a city's timezone already tracks its longitude closely
     * enough for this purpose, and pulling in the equation of time would add error-prone code for
     * a correction smaller than the twilight band.
     */
    fun sunTimes(latitude: Double, dayOfYear: Int): SunTimes {
        val day = dayOfYear.coerceIn(1, 366)
        val lat = latitude.coerceIn(-89.9, 89.9)

        // Declination of the sun, Cooper's equation.
        val declination = Math.toRadians(23.45) *
            sin(2.0 * Math.PI * (284 + day) / 365.0)

        val cosHourAngle = -tan(Math.toRadians(lat)) * tan(declination)

        return when {
            cosHourAngle >= 1.0 ->
                SunTimes(0, 0, polarDay = false, polarNight = true)
            cosHourAngle <= -1.0 ->
                SunTimes(0, MINUTES_PER_DAY, polarDay = true, polarNight = false)
            else -> {
                val hourAngle = Math.toDegrees(acos(cosHourAngle)) / 15.0   // hours from noon
                val sunrise = ((12.0 - hourAngle) * 60).roundToInt().coerceIn(0, MINUTES_PER_DAY)
                val sunset = ((12.0 + hourAngle) * 60).roundToInt().coerceIn(0, MINUTES_PER_DAY)
                SunTimes(sunrise, sunset)
            }
        }
    }

    fun isDaytime(minuteOfDay: Int, sun: SunTimes): Boolean = when {
        sun.polarDay -> true
        sun.polarNight -> false
        else -> minuteOfDay >= sun.sunriseMinute && minuteOfDay < sun.sunsetMinute
    }

    fun phase(minuteOfDay: Int, sun: SunTimes): Phase {
        if (sun.polarDay) return Phase.DAY
        if (sun.polarNight) return Phase.NIGHT

        val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY)
        val toSunrise = minute - sun.sunriseMinute
        val toSunset = minute - sun.sunsetMinute

        return when {
            abs(toSunrise) <= TWILIGHT_MINUTES / 2 -> Phase.SUNRISE
            abs(toSunset) <= TWILIGHT_MINUTES / 2 -> Phase.SUNSET
            toSunrise in -TWILIGHT_MINUTES..0 -> Phase.DAWN
            toSunset in 0..TWILIGHT_MINUTES -> Phase.DUSK
            minute > sun.sunriseMinute && minute < sun.sunsetMinute -> Phase.DAY
            else -> Phase.NIGHT
        }
    }

    /**
     * How high the sun is, normalised to 0 (deep night) … 1 (solar noon).
     *
     * The gradient on a city card blends between its night and day palettes with this value, so
     * the colour drifts continuously through the day instead of snapping between six buckets.
     */
    fun daylightFraction(minuteOfDay: Int, sun: SunTimes): Float {
        if (sun.polarDay) return 1f
        if (sun.polarNight) return 0f

        val minute = minuteOfDay.coerceIn(0, MINUTES_PER_DAY).toDouble()
        val sunrise = sun.sunriseMinute.toDouble()
        val sunset = sun.sunsetMinute.toDouble()
        val dayLength = sunset - sunrise
        if (dayLength <= 0.0) return 0f

        return if (minute in sunrise..sunset) {
            // Half sine across the daylight span: 0 at both horizons, 1 at midday.
            sin(Math.PI * (minute - sunrise) / dayLength).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    }

    /** Solar elevation in degrees; negative means below the horizon. Used by the dual widget. */
    fun solarElevationDegrees(latitude: Double, dayOfYear: Int, minuteOfDay: Int): Double {
        val lat = Math.toRadians(latitude.coerceIn(-89.9, 89.9))
        val declination = Math.toRadians(23.45) *
            sin(2.0 * Math.PI * (284 + dayOfYear.coerceIn(1, 366)) / 365.0)
        val hourAngle = Math.toRadians((minuteOfDay / 60.0 - 12.0) * 15.0)
        val elevation = asin(
            sin(lat) * sin(declination) + cos(lat) * cos(declination) * cos(hourAngle)
        )
        return Math.toDegrees(elevation)
    }
}
