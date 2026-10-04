package com.digitalclockpro.clockengine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Finds the hours at which people in different timezones are all inside their working day.
 *
 * Everything is computed on absolute [Instant]s and only converted to local time when a
 * participant is tested, which is what makes the result correct across DST boundaries and across
 * half-hour / quarter-hour offsets (India +5:30, Nepal +5:45, Chatham +12:45).
 *
 * Pure JVM, no Android dependency, so the whole thing is unit-testable.
 */
object MeetingPlanner {

    const val DEFAULT_WORK_START_HOUR = 9
    const val DEFAULT_WORK_END_HOUR = 17

    /** Resolution of the scan. 30 min is enough for every real-world UTC offset. */
    const val SLOT_MINUTES = 30

    private const val SLOTS_PER_DAY = 24 * 60 / SLOT_MINUTES
    private const val MINUTES_PER_DAY = 24 * 60

    /**
     * One person (or one tracked city) in the plan.
     *
     * [workStartHour] is inclusive and [workEndHour] exclusive, so 9..17 means a slot ending
     * exactly at 17:00 still counts but one ending at 17:30 does not.
     */
    data class Participant(
        val id: String,
        val zone: ZoneId,
        val workStartHour: Int = DEFAULT_WORK_START_HOUR,
        val workEndHour: Int = DEFAULT_WORK_END_HOUR
    ) {
        internal val startMinute: Int get() = (workStartHour * 60).coerceIn(0, MINUTES_PER_DAY)
        internal val endMinute: Int get() = (workEndHour * 60).coerceIn(0, MINUTES_PER_DAY)

        /** A zero-length or inverted working day can never match; guarded rather than thrown. */
        internal val isUsable: Boolean get() = endMinute > startMinute
    }

    /** A single [SLOT_MINUTES] block and who can attend it. */
    data class SlotScore(
        val startUtc: Instant,
        val availableIds: Set<String>,
        val totalParticipants: Int
    ) {
        val availableCount: Int get() = availableIds.size
        val isUnanimous: Boolean get() = totalParticipants > 0 && availableCount == totalParticipants
        val endUtc: Instant get() = startUtc.plus(SLOT_MINUTES.toLong(), ChronoUnit.MINUTES)
    }

    /** A maximal run of adjacent slots sharing exactly the same attendee set. */
    data class Window(
        val startUtc: Instant,
        val endUtc: Instant,
        val availableIds: Set<String>,
        val totalParticipants: Int
    ) {
        val availableCount: Int get() = availableIds.size
        val isUnanimous: Boolean get() = totalParticipants > 0 && availableCount == totalParticipants
        val durationMinutes: Long get() = ChronoUnit.MINUTES.between(startUtc, endUtc)
    }

    /**
     * Scores every half hour of [day] as seen from [anchorZone] — normally the user's own zone,
     * so "the day" means their day, not an arbitrary UTC day.
     */
    fun slots(
        participants: List<Participant>,
        day: LocalDate,
        anchorZone: ZoneId
    ): List<SlotScore> {
        if (participants.isEmpty()) return emptyList()
        val dayStart = day.atStartOfDay(anchorZone).toInstant()

        return (0 until SLOTS_PER_DAY).map { index ->
            val start = dayStart.plus((index * SLOT_MINUTES).toLong(), ChronoUnit.MINUTES)
            val end = start.plus(SLOT_MINUTES.toLong(), ChronoUnit.MINUTES)
            val available = participants
                .filter { it.isUsable && covers(it, start, end) }
                .map { it.id }
                .toSet()
            SlotScore(start, available, participants.size)
        }
    }

    /**
     * The best meeting windows of [day].
     *
     * Only the *highest achievable* attendance is considered: if all four cities can meet, a
     * three-city window is never offered. Windows shorter than [minimumMinutes] are dropped
     * unless that would leave nothing to show, in which case the longest ones are returned
     * anyway — an honest "best we could do" beats an empty screen.
     */
    fun bestWindows(
        participants: List<Participant>,
        day: LocalDate,
        anchorZone: ZoneId,
        minimumMinutes: Int = 120,
        maxResults: Int = 3
    ): List<Window> {
        val slots = slots(participants, day, anchorZone)
        if (slots.isEmpty()) return emptyList()

        val best = slots.maxOf { it.availableCount }
        if (best == 0) return emptyList()

        val runs = mergeRuns(slots.filter { it.availableCount == best })
        val longEnough = runs.filter { it.durationMinutes >= minimumMinutes }
        val candidates = longEnough.ifEmpty { runs }

        return candidates
            .sortedWith(
                compareByDescending<Window> { it.durationMinutes }.thenBy { it.startUtc }
            )
            .take(maxResults)
    }

    /**
     * True when the *whole* slot falls inside the participant's working day in their own zone.
     * Testing both edges is what stops a 30-minute block from straddling 17:00.
     */
    private fun covers(participant: Participant, start: Instant, end: Instant): Boolean {
        val localStart = start.atZone(participant.zone).toLocalTime()
        val startMinuteOfDay = localStart.hour * 60 + localStart.minute

        val localEnd = end.atZone(participant.zone).toLocalTime()
        var endMinuteOfDay = localEnd.hour * 60 + localEnd.minute
        // Midnight comes back as 0; for an end bound that means "end of this day".
        if (endMinuteOfDay <= startMinuteOfDay) endMinuteOfDay = MINUTES_PER_DAY

        return startMinuteOfDay >= participant.startMinute &&
            endMinuteOfDay <= participant.endMinute
    }

    /** Collapses adjacent slots that have an identical attendee set into one [Window]. */
    private fun mergeRuns(slots: List<SlotScore>): List<Window> {
        if (slots.isEmpty()) return emptyList()
        val windows = mutableListOf<Window>()

        var runStart = slots.first().startUtc
        var runEnd = slots.first().endUtc
        var runIds = slots.first().availableIds
        val total = slots.first().totalParticipants

        for (slot in slots.drop(1)) {
            val contiguous = slot.startUtc == runEnd && slot.availableIds == runIds
            if (contiguous) {
                runEnd = slot.endUtc
            } else {
                windows += Window(runStart, runEnd, runIds, total)
                runStart = slot.startUtc
                runEnd = slot.endUtc
                runIds = slot.availableIds
            }
        }
        windows += Window(runStart, runEnd, runIds, total)
        return windows
    }
}
