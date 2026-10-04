package com.digitalclockpro.clockengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetRedrawGateTest {

    private fun sig(
        configHash: Int = 1,
        timeBucket: Long = 100L,
        battery: Int? = 80,
        alarm: String? = "06:30",
        weather: String? = "21°C",
        w: Int = 250,
        h: Int = 110
    ) = WidgetSignature(configHash, timeBucket, battery, alarm, weather, w, h)

    // ---------------------------------------------------------------- timeBucket

    @Test
    fun `without seconds every timestamp in the same minute shares a bucket`() {
        val base = 1_700_000_040L // :00 of some minute
        val buckets = (0 until 60).map { timeBucket(base + it, showSeconds = false) }.toSet()
        assertEquals(1, buckets.size)
    }

    @Test
    fun `without seconds the bucket advances at the minute boundary`() {
        val base = 1_700_000_040L
        assertTrue(
            timeBucket(base + 60, showSeconds = false) >
                timeBucket(base, showSeconds = false)
        )
    }

    @Test
    fun `with seconds every second is its own bucket`() {
        val base = 1_700_000_040L
        val buckets = (0 until 60).map { timeBucket(base + it, showSeconds = true) }.toSet()
        assertEquals(60, buckets.size)
    }

    // ---------------------------------------------------------------- gate basics

    @Test
    fun `first ever draw is always allowed`() {
        assertTrue(RedrawGate().shouldRedraw(1, sig()))
    }

    @Test
    fun `an identical signature is skipped`() {
        val gate = RedrawGate()
        assertTrue(gate.shouldRedraw(1, sig()))
        assertFalse(gate.shouldRedraw(1, sig()))
    }

    @Test
    fun `repeated identical signatures stay skipped`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig())
        repeat(50) { assertFalse(gate.shouldRedraw(1, sig())) }
    }

    @Test
    fun `a new minute redraws`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig(timeBucket = 100L))
        assertTrue(gate.shouldRedraw(1, sig(timeBucket = 101L)))
    }

    @Test
    fun `a battery percent change redraws`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig(battery = 80))
        assertTrue(gate.shouldRedraw(1, sig(battery = 79)))
    }

    @Test
    fun `a config change redraws`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig(configHash = 1))
        assertTrue(gate.shouldRedraw(1, sig(configHash = 2)))
    }

    @Test
    fun `a resize redraws`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig(w = 250, h = 110))
        assertTrue(gate.shouldRedraw(1, sig(w = 320, h = 110)))
        assertTrue(gate.shouldRedraw(1, sig(w = 320, h = 180)))
    }

    @Test
    fun `next alarm text change redraws`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig(alarm = "06:30"))
        assertTrue(gate.shouldRedraw(1, sig(alarm = "07:00")))
    }

    @Test
    fun `clearing the next alarm redraws`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig(alarm = "06:30"))
        assertTrue(gate.shouldRedraw(1, sig(alarm = null)))
    }

    @Test
    fun `weather change redraws`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig(weather = "21°C"))
        assertTrue(gate.shouldRedraw(1, sig(weather = "22°C")))
    }

    // ---------------------------------------------------------------- isolation

    @Test
    fun `widgets are tracked independently`() {
        val gate = RedrawGate()
        assertTrue(gate.shouldRedraw(1, sig()))
        // Same signature, different widget: must still draw, it has never been drawn.
        assertTrue(gate.shouldRedraw(2, sig()))
        assertFalse(gate.shouldRedraw(1, sig()))
        assertFalse(gate.shouldRedraw(2, sig()))
    }

    @Test
    fun `one widget changing does not unblock another`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig())
        gate.shouldRedraw(2, sig())
        assertTrue(gate.shouldRedraw(1, sig(timeBucket = 101L)))
        assertFalse(gate.shouldRedraw(2, sig()))
    }

    // ---------------------------------------------------------------- force

    @Test
    fun `force overrides an identical signature`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig())
        assertFalse(gate.shouldRedraw(1, sig()))
        assertTrue(gate.shouldRedraw(1, sig(), force = true))
    }

    @Test
    fun `force still records the signature so the next unforced call is skipped`() {
        val gate = RedrawGate()
        assertTrue(gate.shouldRedraw(1, sig(), force = true))
        assertFalse(gate.shouldRedraw(1, sig()))
    }

    // ---------------------------------------------------------------- housekeeping

    @Test
    fun `forget makes the widget draw again`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig())
        gate.forget(1)
        assertTrue(gate.shouldRedraw(1, sig()))
    }

    @Test
    fun `forget does not grow the map and does not affect others`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig())
        gate.shouldRedraw(2, sig())
        assertEquals(2, gate.trackedCount())
        gate.forget(1)
        assertEquals(1, gate.trackedCount())
        assertFalse(gate.shouldRedraw(2, sig()))
    }

    @Test
    fun `forgetting an unknown id is harmless`() {
        val gate = RedrawGate()
        gate.forget(999)
        assertEquals(0, gate.trackedCount())
    }

    @Test
    fun `invalidateAll redraws everything once`() {
        val gate = RedrawGate()
        gate.shouldRedraw(1, sig())
        gate.shouldRedraw(2, sig())
        gate.invalidateAll()
        assertTrue(gate.shouldRedraw(1, sig()))
        assertTrue(gate.shouldRedraw(2, sig()))
        assertFalse(gate.shouldRedraw(1, sig()))
    }

    // ---------------------------------------------------------------- battery filter

    @Test
    fun `the first battery reading is accepted`() {
        assertTrue(BatteryLevelFilter().accept(80, 100))
    }

    @Test
    fun `the same percent is rejected`() {
        val f = BatteryLevelFilter()
        f.accept(80, 100)
        assertFalse(f.accept(80, 100))
    }

    @Test
    fun `a different percent is accepted`() {
        val f = BatteryLevelFilter()
        f.accept(80, 100)
        assertTrue(f.accept(81, 100))
    }

    @Test
    fun `readings that round to the same integer percent are rejected`() {
        val f = BatteryLevelFilter()
        // 4000/5000 and 4004/5000 both floor to 80%.
        assertTrue(f.accept(4000, 5000))
        assertFalse(f.accept(4004, 5000))
        assertEquals(80, f.percentOf(4004, 5000))
    }

    @Test
    fun `a charging storm of identical percents yields exactly one redraw`() {
        val f = BatteryLevelFilter()
        val accepted = (0 until 100).count { f.accept(77, 100) }
        assertEquals(1, accepted)
    }

    @Test
    fun `a slow charge from 10 to 20 percent yields exactly eleven redraws`() {
        val f = BatteryLevelFilter()
        var accepted = 0
        for (p in 10..20) {
            // The framework repeats each level many times; only the change should count.
            repeat(25) { if (f.accept(p, 100)) accepted++ }
        }
        assertEquals(11, accepted)
    }

    @Test
    fun `a malformed reading is passed through rather than swallowed`() {
        val f = BatteryLevelFilter()
        assertTrue(f.accept(-1, 100))
        assertTrue(f.accept(50, 0))
        assertTrue(f.accept(50, -3))
    }

    @Test
    fun `a malformed reading does not poison the remembered percent`() {
        val f = BatteryLevelFilter()
        f.accept(80, 100)
        f.accept(-1, 100)
        // 80% is still the last *valid* reading, so it must stay suppressed.
        assertFalse(f.accept(80, 100))
    }

    @Test
    fun `percentOf clamps out of range scales`() {
        val f = BatteryLevelFilter()
        assertEquals(100, f.percentOf(200, 100))
        assertEquals(0, f.percentOf(0, 100))
        assertEquals(null, f.percentOf(10, 0))
    }

    @Test
    fun `reset accepts the next reading again`() {
        val f = BatteryLevelFilter()
        f.accept(80, 100)
        f.reset()
        assertTrue(f.accept(80, 100))
    }

    // ---------------------------------------------------------------- realistic sequence

    @Test
    fun `an idle minute with a battery storm produces one draw not sixty`() {
        val gate = RedrawGate()
        val base = 1_700_000_040L
        var draws = 0
        // Same minute, same battery, 60 broadcasts.
        for (s in 0 until 60) {
            val signature = sig(timeBucket = timeBucket(base + s, showSeconds = false))
            if (gate.shouldRedraw(1, signature)) draws++
        }
        assertEquals(1, draws)
    }

    @Test
    fun `an hour of minute ticks produces exactly sixty draws`() {
        val gate = RedrawGate()
        val base = 1_700_000_040L
        var draws = 0
        for (m in 0 until 60) {
            val signature = sig(timeBucket = timeBucket(base + m * 60, showSeconds = false))
            if (gate.shouldRedraw(1, signature)) draws++
        }
        assertEquals(60, draws)
    }
}
