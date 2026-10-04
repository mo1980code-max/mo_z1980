package com.digitalclockpro.oem

import com.digitalclockpro.R
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
        // Nothing required is missing, so there is no issue text to show.
        assertTrue(s.issues.isEmpty())
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
    fun `issues list every missing required item in display order`() {
        // Resource ids, not text: the data class is locale-agnostic and the UI resolves them,
        // which is what makes the Arabic translation work without touching this logic.
        assertEquals(
            listOf(
                R.string.reliability_issue_notifications,
                R.string.reliability_issue_exact_alarms,
                R.string.reliability_issue_fullscreen
            ),
            status(notifications = false, exact = false, fullScreen = false, battery = false)
                .issues
        )
    }

    @Test
    fun `battery optimization never appears as an issue`() {
        assertTrue(status(battery = false).issues.isEmpty())
    }
}
