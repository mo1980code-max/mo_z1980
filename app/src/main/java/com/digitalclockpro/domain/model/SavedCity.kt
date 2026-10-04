package com.digitalclockpro.domain.model

import com.digitalclockpro.clockengine.DayNight
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * How a city's clock relates to the user's own.
 *
 * Returned as data rather than as a formatted sentence so the presentation layer can translate
 * it — "3 hr ahead of you" has a different word order in Arabic, and a domain model has no
 * business knowing about either.
 */
sealed interface TimeDifference {
    /** Same wall-clock time as the user, which is common across a single country. */
    data object Same : TimeDifference
    data class Ahead(val hours: Long, val minutes: Long) : TimeDifference
    data class Behind(val hours: Long, val minutes: Long) : TimeDifference
}

/** Calendar day of a city relative to the user's day. */
enum class DayOffset { YESTERDAY, TODAY, TOMORROW, OTHER }

/** A city the user tracks in the World Clock hub. */
data class SavedCity(
    val id: Long = 0L,
    val cityName: String,
    val country: String,
    val zoneId: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val sortOrder: Int = 0,
    val isHome: Boolean = false
) {
    val zone: ZoneId get() = ZoneId.of(zoneId)

    fun nowAt(reference: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime =
        reference.withZoneSameInstant(zone)

    /** UTC offset label, e.g. "UTC+3" or "UTC-5:30". Digits only, so it needs no translation. */
    fun utcOffsetLabel(reference: ZonedDateTime = ZonedDateTime.now()): String {
        val seconds = nowAt(reference).offset.totalSeconds
        val sign = if (seconds < 0) "-" else "+"
        val abs = kotlin.math.abs(seconds)
        val h = abs / 3600
        val m = (abs % 3600) / 60
        return if (m == 0) "UTC$sign$h" else "UTC$sign$h:%02d".format(m)
    }

    /** Signed offset in minutes versus [home]; positive means this city is ahead. */
    fun offsetMinutesFrom(home: ZoneId, reference: ZonedDateTime = ZonedDateTime.now()): Long {
        val here = reference.withZoneSameInstant(home).offset.totalSeconds
        val there = nowAt(reference).offset.totalSeconds
        return (there - here) / 60L
    }

    /** Difference versus the user's zone, as structured data for the UI to phrase. */
    fun difference(home: ZoneId, reference: ZonedDateTime = ZonedDateTime.now()): TimeDifference {
        val diff = Duration.ofMinutes(offsetMinutesFrom(home, reference))
        if (diff.isZero) return TimeDifference.Same
        val abs = diff.abs()
        val hours = abs.toHours()
        val minutes = abs.toMinutes() % 60
        return if (diff.isNegative) TimeDifference.Behind(hours, minutes)
        else TimeDifference.Ahead(hours, minutes)
    }

    /** Whether this city is on the user's day, the one before, or the one after. */
    fun dayOffset(home: ZoneId, reference: ZonedDateTime = ZonedDateTime.now()): DayOffset =
        when (
            nowAt(reference).toLocalDate().toEpochDay() -
                reference.withZoneSameInstant(home).toLocalDate().toEpochDay()
        ) {
            -1L -> DayOffset.YESTERDAY
            0L -> DayOffset.TODAY
            1L -> DayOffset.TOMORROW
            else -> DayOffset.OTHER
        }

    // ------------------------------------------------------------------ sun

    /**
     * Sunrise/sunset for this city on the referenced day.
     *
     * Cities imported without coordinates fall back to a flat 06:00/18:00 rather than pretending
     * to be on the equator, which would make a Nordic city look wrong in December.
     */
    fun sunTimes(reference: ZonedDateTime = ZonedDateTime.now()): DayNight.SunTimes {
        if (latitude == 0.0 && longitude == 0.0) return DayNight.DEFAULT_SUN_TIMES
        val local = nowAt(reference)
        return DayNight.sunTimes(latitude, local.dayOfYear)
    }

    fun minuteOfDay(reference: ZonedDateTime = ZonedDateTime.now()): Int =
        nowAt(reference).let { it.hour * 60 + it.minute }

    /** Real sunrise-based day/night flag, used for the sun/moon icon. */
    fun isDaytime(reference: ZonedDateTime = ZonedDateTime.now()): Boolean =
        DayNight.isDaytime(minuteOfDay(reference), sunTimes(reference))

    /** Dawn / sunrise / day / sunset / dusk / night — drives the card gradient. */
    fun dayPhase(reference: ZonedDateTime = ZonedDateTime.now()): DayNight.Phase =
        DayNight.phase(minuteOfDay(reference), sunTimes(reference))

    /** 0 at deep night, 1 at solar noon. Blends the card gradient continuously. */
    fun daylightFraction(reference: ZonedDateTime = ZonedDateTime.now()): Float =
        DayNight.daylightFraction(minuteOfDay(reference), sunTimes(reference))
}

/** Entry in the bundled offline IANA city catalogue (search source). */
data class CityCatalogEntry(
    val cityName: String,
    val country: String,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double
)
