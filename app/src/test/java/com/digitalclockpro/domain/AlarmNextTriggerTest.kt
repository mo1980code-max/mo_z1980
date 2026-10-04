package com.digitalclockpro.domain

import com.digitalclockpro.domain.model.Alarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmNextTriggerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Amman")

    private fun at(text: String) = LocalDateTime.parse(text)

    @Test
    fun `one-shot alarm later today fires today`() {
        val alarm = Alarm(hour = 23, minute = 30)
        val now = at("2026-10-04T08:00:00")
        val next = alarm.nextTriggerAtMillis(now, zone)
        assertEquals(at("2026-10-04T23:30:00").atZone(zone).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `one-shot alarm already passed rolls over to tomorrow`() {
        val alarm = Alarm(hour = 6, minute = 0)
        val now = at("2026-10-04T08:00:00")
        val next = alarm.nextTriggerAtMillis(now, zone)
        assertEquals(at("2026-10-05T06:00:00").atZone(zone).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `weekday alarm skips the weekend`() {
        // 2026-10-04 is a Sunday.
        val alarm = Alarm(
            hour = 7, minute = 0,
            repeatDays = setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            )
        )
        val next = alarm.nextTriggerAtMillis(at("2026-10-04T09:00:00"), zone)
        assertEquals(at("2026-10-05T07:00:00").atZone(zone).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `skipNextOccurrence jumps one occurrence ahead`() {
        val alarm = Alarm(
            hour = 7, minute = 0,
            repeatDays = DayOfWeek.entries.toSet(),
            skipNextOccurrence = true
        )
        val next = alarm.nextTriggerAtMillis(at("2026-10-04T06:00:00"), zone)
        assertEquals(at("2026-10-05T07:00:00").atZone(zone).toInstant().toEpochMilli(), next)
    }

    @Test
    fun `next trigger is always in the future`() {
        val now = LocalDateTime.now()
        (0..23).forEach { hour ->
            val alarm = Alarm(hour = hour, minute = 0)
            assertTrue(alarm.nextTriggerAtMillis(now) > System.currentTimeMillis() - 1000)
        }
    }
}
