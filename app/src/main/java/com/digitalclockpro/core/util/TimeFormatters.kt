package com.digitalclockpro.core.util

import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object TimeFormatters {
    private val cache = ConcurrentHashMap<String, DateTimeFormatter>()

    fun pattern(pattern: String, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        cache.getOrPut("$pattern|$locale") { DateTimeFormatter.ofPattern(pattern, locale) }

    fun timePattern(use24h: Boolean, showSeconds: Boolean): String = when {
        use24h && showSeconds -> "HH:mm:ss"
        use24h -> "HH:mm"
        showSeconds -> "h:mm:ss"
        else -> "h:mm"
    }

    fun formatTime(dateTime: ZonedDateTime, use24h: Boolean, showSeconds: Boolean): String =
        dateTime.format(pattern(timePattern(use24h, showSeconds)))

    fun formatTime(dateTime: LocalDateTime, use24h: Boolean, showSeconds: Boolean): String =
        dateTime.format(pattern(timePattern(use24h, showSeconds)))

    fun amPm(dateTime: ZonedDateTime): String = dateTime.format(pattern("a")).uppercase(Locale.getDefault())

    fun formatDate(dateTime: ZonedDateTime, datePattern: String): String =
        runCatching { dateTime.format(pattern(datePattern)) }.getOrElse { dateTime.toLocalDate().toString() }

    /** "in 7 h 20 min" style countdown used on the dashboard + alarm list. */
    fun countdown(fromMillis: Long, toMillis: Long): String {
        val delta = (toMillis - fromMillis).coerceAtLeast(0)
        val totalMinutes = delta / 60_000
        val days = totalMinutes / (60 * 24)
        val hours = (totalMinutes % (60 * 24)) / 60
        val minutes = totalMinutes % 60
        return buildString {
            append("in ")
            if (days > 0) append("$days d ")
            if (hours > 0) append("$hours h ")
            append("$minutes min")
        }
    }

    val dayOfWeekLabels: List<String>
        get() = java.time.DayOfWeek.entries.map {
            it.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        }

    val commonDatePatterns = listOf(
        "EEE, MMM d",
        "EEEE, d MMMM",
        "dd/MM/yyyy",
        "MM/dd/yyyy",
        "yyyy-MM-dd",
        "d MMM yyyy",
        "EEE d",
        "MMMM d, yyyy"
    )
}
