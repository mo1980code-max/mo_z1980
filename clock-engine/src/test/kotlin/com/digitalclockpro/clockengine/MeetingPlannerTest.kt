package com.digitalclockpro.clockengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class MeetingPlannerTest {

    private val day = LocalDate.of(2026, 3, 10)      // a plain Tuesday, no DST transition
    private val utc = ZoneId.of("UTC")

    private fun participant(id: String, zone: String, start: Int = 9, end: Int = 17) =
        MeetingPlanner.Participant(id, ZoneId.of(zone), start, end)

    private fun utcHourOf(instant: java.time.Instant): Double {
        val t = instant.atZone(ZoneOffset.UTC).toLocalTime()
        return t.hour + t.minute / 60.0
    }

    // ---------------------------------------------------------------- slots

    @Test
    fun `a day is scanned in 48 half-hour slots`() {
        val slots = MeetingPlanner.slots(listOf(participant("a", "UTC")), day, utc)
        assertEquals(48, slots.size)
        assertEquals(30L, java.time.temporal.ChronoUnit.MINUTES.between(slots[0].startUtc, slots[1].startUtc))
    }

    @Test
    fun `no participants means no slots`() {
        assertTrue(MeetingPlanner.slots(emptyList(), day, utc).isEmpty())
    }

    @Test
    fun `a single participant is available exactly during their working hours`() {
        val slots = MeetingPlanner.slots(listOf(participant("a", "UTC")), day, utc)
        val available = slots.filter { it.availableCount == 1 }
        // 09:00 -> 17:00 is 8 hours = 16 slots.
        assertEquals(16, available.size)
        assertEquals(9.0, utcHourOf(available.first().startUtc), 0.001)
        assertEquals(16.5, utcHourOf(available.last().startUtc), 0.001)
    }

    @Test
    fun `a slot straddling the end of the working day does not count`() {
        val slots = MeetingPlanner.slots(listOf(participant("a", "UTC", 9, 17)), day, utc)
        val straddling = slots.first { utcHourOf(it.startUtc) == 17.0 }
        assertTrue(straddling.availableIds.isEmpty())
    }

    @Test
    fun `an inverted working day is ignored rather than throwing`() {
        val slots = MeetingPlanner.slots(listOf(participant("a", "UTC", 17, 9)), day, utc)
        assertTrue(slots.all { it.availableIds.isEmpty() })
    }

    // ---------------------------------------------------------------- windows

    @Test
    fun `two people in the same zone share their whole working day`() {
        val windows = MeetingPlanner.bestWindows(
            listOf(participant("a", "UTC"), participant("b", "Europe/London")),
            day, utc
        )
        // London is on GMT in March before the clocks change, so the zones coincide.
        val best = windows.first()
        assertTrue(best.isUnanimous)
        assertEquals(9.0, utcHourOf(best.startUtc), 0.001)
        assertEquals(17.0, utcHourOf(best.endUtc), 0.001)
        assertEquals(480L, best.durationMinutes)
    }

    @Test
    fun `a three hour offset leaves a five hour overlap`() {
        val windows = MeetingPlanner.bestWindows(
            listOf(participant("london", "UTC"), participant("amman", "Asia/Amman")),
            day, utc
        )
        val best = windows.first()
        assertTrue(best.isUnanimous)
        // Amman is UTC+3 in March, so its 09:00-17:00 is 06:00-14:00 UTC.
        assertEquals(9.0, utcHourOf(best.startUtc), 0.001)
        assertEquals(14.0, utcHourOf(best.endUtc), 0.001)
    }

    @Test
    fun `opposite sides of the planet have no common working hour`() {
        val windows = MeetingPlanner.bestWindows(
            listOf(participant("a", "UTC"), participant("b", "Asia/Shanghai")),
            day, utc
        )
        // Shanghai is UTC+8: 01:00-09:00 UTC, which ends exactly when UTC's day starts.
        assertTrue(windows.isNotEmpty())
        assertFalse(windows.any { it.isUnanimous })
        assertEquals(1, windows.first().availableCount)
    }

    @Test
    fun `half hour offsets are handled exactly`() {
        val windows = MeetingPlanner.bestWindows(
            listOf(participant("a", "UTC"), participant("b", "Asia/Kolkata")),
            day, utc
        )
        val best = windows.first()
        assertTrue(best.isUnanimous)
        // Kolkata is UTC+5:30 -> 03:30-11:30 UTC; overlap with 09:00-17:00 is 09:00-11:30.
        assertEquals(9.0, utcHourOf(best.startUtc), 0.001)
        assertEquals(11.5, utcHourOf(best.endUtc), 0.001)
        assertEquals(150L, best.durationMinutes)
    }

    @Test
    fun `quarter hour offsets are handled exactly`() {
        val windows = MeetingPlanner.bestWindows(
            listOf(participant("a", "UTC"), participant("b", "Asia/Kathmandu")),
            day, utc
        )
        val best = windows.first()
        assertTrue(best.isUnanimous)
        // Kathmandu is UTC+5:45 -> 03:15-11:15 UTC. The slot grid is on the half hour, so the
        // first fully-contained slot starts at 09:00 and the last usable one ends at 11:00.
        assertEquals(9.0, utcHourOf(best.startUtc), 0.001)
        assertEquals(11.0, utcHourOf(best.endUtc), 0.001)
    }

    @Test
    fun `three cities are reduced to their common core`() {
        val windows = MeetingPlanner.bestWindows(
            listOf(
                participant("london", "UTC"),
                participant("amman", "Asia/Amman"),      // UTC+3 -> 06:00-14:00
                participant("berlin", "Europe/Berlin")   // UTC+1 -> 08:00-16:00
            ),
            day, utc
        )
        val best = windows.first()
        assertTrue(best.isUnanimous)
        assertEquals(3, best.availableCount)
        assertEquals(9.0, utcHourOf(best.startUtc), 0.001)
        assertEquals(14.0, utcHourOf(best.endUtc), 0.001)
    }

    @Test
    fun `windows shorter than the minimum are dropped when a longer one exists`() {
        val participants = listOf(participant("a", "UTC"), participant("b", "Asia/Kolkata"))
        val strict = MeetingPlanner.bestWindows(participants, day, utc, minimumMinutes = 180)
        // The only unanimous span is 150 minutes, so nothing meets a 3-hour minimum and the
        // planner falls back to showing it anyway instead of an empty result.
        assertEquals(1, strict.size)
        assertEquals(150L, strict.first().durationMinutes)
    }

    @Test
    fun `results are capped and ordered by length`() {
        val windows = MeetingPlanner.bestWindows(
            listOf(participant("a", "UTC"), participant("b", "Asia/Shanghai")),
            day, utc, minimumMinutes = 30, maxResults = 2
        )
        assertTrue(windows.size <= 2)
        if (windows.size == 2) {
            assertTrue(windows[0].durationMinutes >= windows[1].durationMinutes)
        }
    }

    @Test
    fun `custom working hours are respected`() {
        val windows = MeetingPlanner.bestWindows(
            listOf(
                participant("early", "UTC", start = 6, end = 12),
                participant("late", "UTC", start = 10, end = 20)
            ),
            day, utc
        )
        val best = windows.first()
        assertTrue(best.isUnanimous)
        assertEquals(10.0, utcHourOf(best.startUtc), 0.001)
        assertEquals(12.0, utcHourOf(best.endUtc), 0.001)
    }

    @Test
    fun `the anchor zone defines which day is scanned`() {
        val participants = listOf(participant("a", "Pacific/Auckland"))
        val fromAuckland = MeetingPlanner.slots(participants, day, ZoneId.of("Pacific/Auckland"))
        // Scanned in the participant's own zone, their whole working day is inside the scan.
        assertEquals(16, fromAuckland.count { it.availableCount == 1 })
    }

    @Test
    fun `a window never reports more attendees than participants`() {
        val participants = listOf(participant("a", "UTC"), participant("b", "Asia/Amman"))
        MeetingPlanner.bestWindows(participants, day, utc).forEach {
            assertTrue(it.availableCount <= it.totalParticipants)
        }
    }
}
