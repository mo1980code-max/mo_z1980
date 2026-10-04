package com.digitalclockpro.clockengine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * All analog-clock geometry, expressed as pure functions.
 *
 * Zero Android imports on purpose: the Compose `Canvas` in the app and the `android.graphics`
 * bitmap renderer used by the widget both consume these same numbers, which is what keeps the
 * two representations pixel-consistent — and lets every rule be unit-tested on the JVM.
 *
 * Angle convention: **degrees clockwise from 12 o'clock**. Call [toRadiansFromTwelve] before
 * feeding them to `cos`/`sin`.
 */
object ClockEngine {

    /** How the hands advance. */
    enum class HandMotion {
        /** Real quartz movement: the second hand jumps once per second. */
        QUARTZ,

        /** Sweeping (automatic-watch) movement: continuous, sub-second interpolation. */
        SMOOTH
    }

    /** Hand rotations in degrees clockwise from 12 o'clock. */
    data class HandAngles(
        val hourDegrees: Float,
        val minuteDegrees: Float,
        val secondDegrees: Float
    )

    /** A point on the dial, in pixels, relative to the canvas origin. */
    data class DialPoint(val x: Float, val y: Float)

    const val DEGREES_PER_HOUR = 30f        // 360 / 12
    const val DEGREES_PER_MINUTE = 6f       // 360 / 60
    const val DEGREES_PER_SECOND = 6f

    /**
     * Computes the three hand angles.
     *
     * @param hour       0..23 (12-hour wrap is applied internally).
     * @param minute     0..59
     * @param second     0..59
     * @param nanosecond 0..999_999_999, only used by [HandMotion.SMOOTH].
     *
     * In [HandMotion.QUARTZ] the second hand snaps to whole seconds and the minute hand to whole
     * minutes, while the hour hand still creeps with the minutes — exactly how a real quartz
     * movement behaves (the hour hand is geared to the minute hand, not stepped).
     */
    fun handAngles(
        hour: Int,
        minute: Int,
        second: Int,
        nanosecond: Int = 0,
        motion: HandMotion = HandMotion.QUARTZ
    ): HandAngles {
        val h = ((hour % 12) + 12) % 12
        val m = minute.coerceIn(0, 59)
        val s = second.coerceIn(0, 59)

        return when (motion) {
            HandMotion.QUARTZ -> HandAngles(
                hourDegrees = h * DEGREES_PER_HOUR + m * 0.5f,
                minuteDegrees = m * DEGREES_PER_MINUTE,
                secondDegrees = s * DEGREES_PER_SECOND
            )

            HandMotion.SMOOTH -> {
                val fractionalSecond = s + (nanosecond.coerceIn(0, 999_999_999) / 1_000_000_000f)
                val fractionalMinute = m + fractionalSecond / 60f
                val fractionalHour = h + fractionalMinute / 60f
                HandAngles(
                    hourDegrees = fractionalHour * DEGREES_PER_HOUR,
                    minuteDegrees = fractionalMinute * DEGREES_PER_MINUTE,
                    secondDegrees = fractionalSecond * DEGREES_PER_SECOND
                )
            }
        }
    }

    /**
     * Redraw cadence for a motion.
     *
     * Smooth sweeping needs ~60 fps, which is only ever acceptable **on screen**; widgets always
     * use [HandMotion.QUARTZ] driven by `ACTION_TIME_TICK` (one redraw per minute) so they cost
     * nothing in battery.
     */
    fun frameIntervalMillis(motion: HandMotion): Long = when (motion) {
        HandMotion.QUARTZ -> 1_000L
        HandMotion.SMOOTH -> 16L
    }

    /** Degrees clockwise from 12 -> radians usable with `cos`/`sin` on a screen coordinate system. */
    fun toRadiansFromTwelve(degrees: Float): Float =
        ((degrees - 90f) * PI / 180.0).toFloat()

    /**
     * Point at [radius] from ([centerX], [centerY]) along [degrees] clockwise from 12 o'clock.
     * Used for hand tips, tick marks and numeral placement alike.
     */
    fun pointOnDial(
        centerX: Float,
        centerY: Float,
        radius: Float,
        degrees: Float
    ): DialPoint {
        val radians = toRadiansFromTwelve(degrees)
        return DialPoint(
            x = centerX + radius * cos(radians),
            y = centerY + radius * sin(radians)
        )
    }

    /** Angle of the tick mark with index [index] on a dial of [totalTicks] marks. */
    fun tickAngle(index: Int, totalTicks: Int = 60): Float {
        require(totalTicks > 0) { "totalTicks must be positive" }
        return (360f / totalTicks) * index
    }

    /** True for the 12 long marks on a 60-tick dial (every 5th), used to draw them thicker. */
    fun isMajorTick(index: Int, totalTicks: Int = 60): Boolean {
        if (totalTicks % 12 != 0) return true
        return index % (totalTicks / 12) == 0
    }

    /** 1..12 numeral for a major tick index, so 0 renders as "12" and not "0". */
    fun numeralForTick(index: Int, totalTicks: Int = 60): Int {
        val perNumeral = totalTicks / 12
        val numeral = index / perNumeral
        return if (numeral == 0) 12 else numeral
    }

    private val ROMAN = arrayOf(
        "XII", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI"
    )

    /** Roman numeral for hour [hour] (1..12, or 0 treated as 12). Used by the Classic Roman face. */
    fun romanNumeral(hour: Int): String = ROMAN[((hour % 12) + 12) % 12]

    // ------------------------------------------------------------------ burn-in protection

    /**
     * Slow drift applied to a full-screen clock so a static image never sits on the same OLED
     * pixels. The position walks a Lissajous curve (x period 1x, y period 2x) rather than a
     * circle, so the path does not repeat every cycle and no pixel is favoured.
     *
     * @param elapsedMillis time since the screen was opened.
     * @param amplitudeX    maximum horizontal displacement in pixels.
     * @param amplitudeY    maximum vertical displacement in pixels.
     * @param periodMillis  time for one full horizontal sweep; one minute by default, which is
     *        slow enough to be invisible yet moves the image every single frame-minute.
     */
    fun burnInOffset(
        elapsedMillis: Long,
        amplitudeX: Float,
        amplitudeY: Float,
        periodMillis: Long = DEFAULT_BURN_IN_PERIOD_MILLIS
    ): DialPoint {
        if (periodMillis <= 0L) return DialPoint(0f, 0f)
        val phase = (elapsedMillis.toDouble() % periodMillis) / periodMillis * 2 * PI
        return DialPoint(
            x = (amplitudeX * sin(phase)).toFloat(),
            y = (amplitudeY * sin(2 * phase)).toFloat()
        )
    }

    /** One full horizontal drift per minute. */
    const val DEFAULT_BURN_IN_PERIOD_MILLIS = 60_000L

    /**
     * Maps a vertical drag to a screen brightness value.
     *
     * Dragging **up** brightens. The result is clamped to [MIN_BRIGHTNESS]..1f: zero would make
     * some OEM ROMs blank the panel entirely, leaving the user unable to drag back.
     */
    fun brightnessAfterDrag(
        current: Float,
        dragDeltaPx: Float,
        screenHeightPx: Float
    ): Float {
        if (screenHeightPx <= 0f) return current.coerceIn(MIN_BRIGHTNESS, 1f)
        val delta = -dragDeltaPx / screenHeightPx
        return (current + delta).coerceIn(MIN_BRIGHTNESS, 1f)
    }

    /** Never fully dark: the user must always be able to find the screen again. */
    const val MIN_BRIGHTNESS = 0.02f

    /** Rounds a brightness fraction to a whole percentage for display. */
    fun brightnessPercent(brightness: Float): Int =
        (brightness.coerceIn(0f, 1f) * 100).roundToInt()
}
