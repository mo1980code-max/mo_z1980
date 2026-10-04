package com.digitalclockpro.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Domain representation of a single alarm. Pure Kotlin – no Android / Room types.
 */
data class Alarm(
    val id: Long = 0L,
    val label: String = "",
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    /** Empty set == one-shot alarm (fires once at the next occurrence). */
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val soundUri: String? = null,
    val soundTitle: String = "",
    val volumePercent: Int = 80,
    val volumeRampSeconds: Int = 30,
    val vibrationPattern: VibrationPattern = VibrationPattern.PULSE,
    val snoozeMinutes: Int = 9,
    val maxSnoozeCount: Int = 3,
    val currentSnoozeCount: Int = 0,
    val challenge: DismissChallenge = DismissChallenge.None,
    /** Skip the very next occurrence without disabling the alarm. */
    val skipNextOccurrence: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
) {
    val isRepeating: Boolean get() = repeatDays.isNotEmpty()

    val time: LocalTime get() = LocalTime.of(hour, minute)

    /**
     * Next trigger instant in epoch millis, honouring repeat days, DST and [skipNextOccurrence].
     */
    fun nextTriggerAtMillis(
        from: LocalDateTime = LocalDateTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long {
        var candidate = computeNext(from, LocalDate.from(from))
        if (skipNextOccurrence) {
            candidate = computeNext(candidate.plusMinutes(1), candidate.toLocalDate())
        }
        return candidate.atZone(zone).toInstant().toEpochMilli()
    }

    private fun computeNext(from: LocalDateTime, startDate: LocalDate): LocalDateTime {
        val target = LocalTime.of(hour, minute)
        if (repeatDays.isEmpty()) {
            val today = LocalDateTime.of(startDate, target)
            return if (today.isAfter(from)) today else today.plusDays(1)
        }
        for (offset in 0..7) {
            val date = startDate.plusDays(offset.toLong())
            if (date.dayOfWeek !in repeatDays) continue
            val dt = LocalDateTime.of(date, target)
            if (dt.isAfter(from)) return dt
        }
        return LocalDateTime.of(startDate.plusDays(7), target)
    }
}

enum class VibrationPattern(val timings: LongArray) {
    NONE(longArrayOf(0)),
    CONTINUOUS(longArrayOf(0, 2000, 200)),
    PULSE(longArrayOf(0, 400, 400)),
    HEARTBEAT(longArrayOf(0, 120, 120, 220, 800)),
    SOS(longArrayOf(0, 150, 150, 150, 150, 150, 450, 450, 150, 450, 150, 450, 450, 150, 150, 150, 150, 150, 900));

    val repeatIndex: Int get() = if (this == NONE) -1 else 0
}

/** Challenge the user must beat before the alarm can be dismissed. */
sealed interface DismissChallenge {
    data object None : DismissChallenge
    data class Math(val difficulty: Difficulty = Difficulty.MEDIUM, val problemCount: Int = 3) : DismissChallenge
    data class Shake(val shakeCount: Int = 20) : DismissChallenge
    data class Sequence(val tiles: Int = 9) : DismissChallenge

    enum class Difficulty { EASY, MEDIUM, HARD }

    companion object {
        const val TYPE_NONE = "none"
        const val TYPE_MATH = "math"
        const val TYPE_SHAKE = "shake"
        const val TYPE_SEQUENCE = "sequence"
    }
}
