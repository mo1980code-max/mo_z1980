package com.digitalclockpro.clockengine

/**
 * Decides whether a widget actually needs to be redrawn.
 *
 * The problem this solves: the tick receiver listened to `ACTION_BATTERY_CHANGED`, which is a
 * sticky broadcast the framework re-sends on every small voltage, temperature or plug-state
 * change — easily dozens of times a minute while charging. Each one ran a full `refreshAll()`,
 * which rebuilt `RemoteViews` for every placed widget, re-rasterised the clock bitmap, and
 * marshalled the whole bundle across process boundaries to the launcher. The displayed pixels
 * were usually byte-for-byte identical.
 *
 * Two independent filters, deliberately kept apart:
 *
 *  - [BatteryLevelFilter] stops the broadcast storm at the source, before any work is scheduled.
 *  - [RedrawGate] is the backstop: it compares what *would* be drawn against what was drawn
 *    last time for that specific widget id, and skips the draw when nothing visible changed.
 *
 * Both are pure Kotlin with no Android imports so they can be unit-tested on the JVM. They live
 * here rather than in `:app` for exactly that reason.
 */

/**
 * Everything that can change a widget's pixels, and nothing that cannot.
 *
 * [configHash] stands in for the whole [com.digitalclockpro.domain.model.WidgetConfig]: it is a
 * data class, so its `hashCode` already folds in every colour, font, toggle and tap action. That
 * keeps this type free of any dependency on the `:app` module.
 *
 * Note what is absent: battery *voltage*, *temperature* and *charging state*. A widget shows an
 * integer percentage, so that is all that is recorded. This is the single biggest saving.
 */
data class WidgetSignature(
    val configHash: Int,
    /** Wall-clock truncated to the precision actually displayed. See [timeBucket]. */
    val timeBucket: Long,
    val batteryPercent: Int?,
    val nextAlarmText: String?,
    val weatherText: String?,
    val widthDp: Int,
    val heightDp: Int
)

/**
 * Collapses a timestamp to the resolution the widget renders at.
 *
 * A widget without seconds repaints identically for a whole minute, so all 60 timestamps must
 * map to one bucket. Integer division handles negative epochs correctly here because widget
 * timestamps are always well after 1970; using [Math.floorDiv] semantics is unnecessary.
 */
fun timeBucket(epochSeconds: Long, showSeconds: Boolean): Long =
    if (showSeconds) epochSeconds else epochSeconds / 60

/**
 * Remembers the last signature drawn per `appWidgetId`.
 *
 * Not thread-safe by design — callers hold it behind a `@Singleton` and touch it from the widget
 * render coroutine. If that ever changes, wrap the map rather than making this class clever.
 */
class RedrawGate {

    private val lastDrawn = HashMap<Int, WidgetSignature>()

    /**
     * True when [appWidgetId] must be redrawn; records [signature] as drawn when it returns true.
     *
     * [force] exists because skipping is only safe for our own refresh broadcasts. When the
     * framework calls `onUpdate` — after a reboot, a launcher restart, or the widget first being
     * placed — the launcher may be holding no `RemoteViews` for us at all, and honouring a stale
     * cache entry would leave the user staring at a blank box. Those paths always pass `true`.
     */
    fun shouldRedraw(appWidgetId: Int, signature: WidgetSignature, force: Boolean = false): Boolean {
        if (!force && lastDrawn[appWidgetId] == signature) return false
        lastDrawn[appWidgetId] = signature
        return true
    }

    /** Drops a removed widget so the map cannot grow without bound. Called from `onDeleted`. */
    fun forget(appWidgetId: Int) {
        lastDrawn.remove(appWidgetId)
    }

    /** Forces the next draw of every widget, e.g. after a theme or locale change. */
    fun invalidateAll() {
        lastDrawn.clear()
    }

    /** Visible for tests and diagnostics. */
    fun trackedCount(): Int = lastDrawn.size
}

/**
 * Suppresses `ACTION_BATTERY_CHANGED` broadcasts that cannot change what is on screen.
 *
 * The framework reports level and scale; the widget shows `level * 100 / scale`. Two broadcasts
 * that round to the same integer percent are invisible to the user, so only the first is let
 * through. A malformed broadcast (negative level, zero scale) is passed through rather than
 * swallowed — better one wasted redraw than a widget frozen on a stale number.
 */
class BatteryLevelFilter {

    private var lastPercent: Int? = null

    fun percentOf(level: Int, scale: Int): Int? =
        if (level < 0 || scale <= 0) null else (level * 100 / scale).coerceIn(0, 100)

    /** True when this battery reading is worth a redraw. */
    fun accept(level: Int, scale: Int): Boolean {
        val percent = percentOf(level, scale) ?: return true
        if (percent == lastPercent) return false
        lastPercent = percent
        return true
    }

    fun reset() {
        lastPercent = null
    }
}
