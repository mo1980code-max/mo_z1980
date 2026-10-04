package com.digitalclockpro.domain

import com.digitalclockpro.domain.model.SavedCity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class SavedCityTest {

    private val amman = SavedCity(cityName = "Amman", country = "Jordan", zoneId = "Asia/Amman")
    private val tokyo = SavedCity(cityName = "Tokyo", country = "Japan", zoneId = "Asia/Tokyo")
    private val reference: ZonedDateTime = ZonedDateTime.parse("2026-01-15T12:00:00+03:00[Asia/Amman]")

    @Test
    fun `utc offset label is formatted`() {
        assertEquals("UTC+3", amman.utcOffsetLabel(reference))
        assertEquals("UTC+9", tokyo.utcOffsetLabel(reference))
    }

    @Test
    fun `relative label reports the delta against home`() {
        assertEquals("6 hr ahead of you", tokyo.relativeLabel(ZoneId.of("Asia/Amman"), reference))
        assertEquals("Same time as you", amman.relativeLabel(ZoneId.of("Asia/Amman"), reference))
    }

    @Test
    fun `day night flag follows local hour`() {
        assertTrue(amman.isDaytime(reference))                 // 12:00 local
        assertFalse(tokyo.isDaytime(reference.withHour(23)))   // 05:00 next day? -> night check
    }

    @Test
    fun `day label is relative to home`() {
        val home = ZoneId.of("Asia/Amman")
        assertEquals("Today", tokyo.dayLabel(home, reference))
    }
}
