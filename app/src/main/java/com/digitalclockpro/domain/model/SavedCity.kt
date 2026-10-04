package com.digitalclockpro.domain.model

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

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

    /** UTC offset label, e.g. "UTC+3" or "UTC-5:30". */
    fun utcOffsetLabel(reference: ZonedDateTime = ZonedDateTime.now()): String {
        val seconds = nowAt(reference).offset.totalSeconds
        val sign = if (seconds < 0) "-" else "+"
        val abs = kotlin.math.abs(seconds)
        val h = abs / 3600
        val m = (abs % 3600) / 60
        return if (m == 0) "UTC$sign$h" else "UTC$sign$h:%02d".format(m)
    }

    /** Relative difference vs. the user's zone, e.g. "3 hours ahead" / "Same time". */
    fun relativeLabel(home: ZoneId, reference: ZonedDateTime = ZonedDateTime.now()): String {
        val here = reference.withZoneSameInstant(home).offset.totalSeconds
        val there = nowAt(reference).offset.totalSeconds
        val diff = Duration.ofSeconds((there - here).toLong())
        if (diff.isZero) return "Same time as you"
        val ahead = !diff.isNegative
        val abs = diff.abs()
        val hours = abs.toHours()
        val minutes = abs.toMinutes() % 60
        val amount = buildString {
            if (hours > 0L) append("$hours hr")
            if (minutes > 0L) {
                if (isNotEmpty()) append(" ")
                append("$minutes min")
            }
        }
        return "$amount ${if (ahead) "ahead of you" else "behind you"}"
    }

    /** Day label relative to home: "Yesterday", "Today", "Tomorrow". */
    fun dayLabel(home: ZoneId, reference: ZonedDateTime = ZonedDateTime.now()): String =
        when (nowAt(reference).toLocalDate().toEpochDay() -
            reference.withZoneSameInstant(home).toLocalDate().toEpochDay()) {
            -1L -> "Yesterday"
            0L -> "Today"
            1L -> "Tomorrow"
            else -> nowAt(reference).toLocalDate().toString()
        }

    /** Crude but offline day/night flag used for the sun/moon icon and card gradient. */
    fun isDaytime(reference: ZonedDateTime = ZonedDateTime.now()): Boolean =
        nowAt(reference).hour in 6..18
}

/** Entry in the bundled offline IANA city catalogue (search source). */
data class CityCatalogEntry(
    val cityName: String,
    val country: String,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double
)
