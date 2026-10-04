package com.digitalclockpro.core.util

import android.content.Context
import com.digitalclockpro.R
import com.digitalclockpro.domain.model.DayOffset
import com.digitalclockpro.domain.model.SavedCity
import com.digitalclockpro.domain.model.TimeDifference
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Turns the World Clock's structured domain values into translated text.
 *
 * Lives in `core/util` rather than in a Composable because the world-clock widgets need exactly
 * the same wording from a `RemoteViews` builder, where there is no Compose context available.
 */
object WorldClockLabels {

    /** e.g. "3 hr ahead of you", "30 min behind you", "Same time as you". */
    fun difference(context: Context, difference: TimeDifference): String = when (difference) {
        TimeDifference.Same -> context.getString(R.string.world_same_time)
        is TimeDifference.Ahead ->
            context.getString(R.string.world_diff_ahead, amount(context, difference.hours, difference.minutes))
        is TimeDifference.Behind ->
            context.getString(R.string.world_diff_behind, amount(context, difference.hours, difference.minutes))
    }

    /** Short signed form for the comparison row, e.g. "+3h", "-5:30", "0". */
    fun shortOffset(minutes: Long): String {
        if (minutes == 0L) return "0"
        val sign = if (minutes < 0) "-" else "+"
        val abs = kotlin.math.abs(minutes)
        val hours = abs / 60
        val rest = abs % 60
        return if (rest == 0L) "${sign}${hours}h" else "$sign$hours:%02d".format(rest)
    }

    fun dayOffset(
        context: Context,
        city: SavedCity,
        home: ZoneId,
        reference: ZonedDateTime
    ): String = when (city.dayOffset(home, reference)) {
        DayOffset.YESTERDAY -> context.getString(R.string.day_yesterday)
        DayOffset.TODAY -> context.getString(R.string.day_today)
        DayOffset.TOMORROW -> context.getString(R.string.day_tomorrow)
        // More than a day apart cannot actually happen between two real zones, but a stored
        // city with a corrupt offset should degrade to a date rather than to a wrong word.
        DayOffset.OTHER -> city.nowAt(reference).toLocalDate().toString()
    }

    private fun amount(context: Context, hours: Long, minutes: Long): String = when {
        hours > 0L && minutes > 0L ->
            context.getString(R.string.world_duration_hm, hours, minutes)
        hours > 0L -> context.getString(R.string.world_duration_h, hours)
        else -> context.getString(R.string.world_duration_m, minutes)
    }
}
