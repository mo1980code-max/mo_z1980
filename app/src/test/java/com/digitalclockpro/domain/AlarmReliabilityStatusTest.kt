package com.digitalclockpro.domain

import com.digitalclockpro.presentation.common.AlarmReliabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmReliabilityStatusTest {

    @Test
    fun `banner is hidden only when everything is granted`() {
        assertTrue(
            AlarmReliabilityStatus(
                notificationsGranted = true,
                exactAlarmsGranted = true,
                batteryOptimizationIgnored = true
            ).allGranted
        )
    }

    @Test
    fun `battery optimization alone keeps the banner visible`() {
        val status = AlarmReliabilityStatus(
            notificationsGranted = true,
            exactAlarmsGranted = true,
            batteryOptimizationIgnored = false
        )
        assertFalse(status.allGranted)
        assertEquals("Battery optimization can freeze the app.", status.explanation)
    }

    @Test
    fun `explanation lists every missing requirement`() {
        val status = AlarmReliabilityStatus(
            notificationsGranted = false,
            exactAlarmsGranted = false,
            batteryOptimizationIgnored = false
        )
        assertEquals(
            "Notifications are blocked, exact alarms are not allowed, " +
                "battery optimization can freeze the app.",
            status.explanation
        )
    }
}
