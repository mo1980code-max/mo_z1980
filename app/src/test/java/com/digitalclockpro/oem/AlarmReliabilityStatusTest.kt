package com.digitalclockpro.oem

import com.digitalclockpro.presentation.common.AlarmReliabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmReliabilityStatusTest {

    private fun status(
        notifications: Boolean = true,
        exact: Boolean = true,
        fullScreen: Boolean = true,
        battery: Boolean = true
    ) = AlarmReliabilityStatus(notifications, exact, fullScreen, battery)

    @Test
    fun `battery optimization is optional and never blocks`() {
        val s = status(battery = false)
        // Required permissions are all present -> the pipeline is considered working.
        assertTrue(s.requiredGranted)
        // …but the banner still offers the optional hardening step.
        assertFalse(s.allSatisfied)
        assertEquals("Alarms are set up correctly.", s.explanation)
    }

    @Test
    fun `banner disappears only when everything is satisfied`() {
        assertTrue(status().allSatisfied)
    }

    @Test
    fun `missing notifications or exact alarms break the pipeline`() {
        assertFalse(status(notifications = false).requiredGranted)
        assertFalse(status(exact = false).requiredGranted)
        assertFalse(status(fullScreen = false).requiredGranted)
    }

    @Test
    fun `explanation lists every missing required item`() {
        assertEquals(
            "Notifications are blocked, exact alarms are not allowed, " +
                "full-screen alarms are blocked.",
            status(notifications = false, exact = false, fullScreen = false, battery = false)
                .explanation
        )
    }
}
