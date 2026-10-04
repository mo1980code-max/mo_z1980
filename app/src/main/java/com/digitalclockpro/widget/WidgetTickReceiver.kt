package com.digitalclockpro.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import com.digitalclockpro.clockengine.BatteryLevelFilter
import com.digitalclockpro.clockengine.RedrawGate
import com.digitalclockpro.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the minute tick / screen / battery broadcasts and redraws the widgets.
 *
 * Does nothing while the screen is off — `ACTION_TIME_TICK` is simply not delivered then, which
 * is exactly the battery behaviour we want.
 *
 * The expensive case is `ACTION_BATTERY_CHANGED`. It is a sticky broadcast the framework re-sends
 * on every voltage, temperature and plug-state change, which while charging means many deliveries
 * per minute. This receiver used to run a full `refreshAll()` for each one, re-rasterising every
 * widget bitmap to display the same integer percentage. [BatteryLevelFilter] now drops those
 * before any work is scheduled; [RedrawGate] catches whatever slips past.
 */
@AndroidEntryPoint
class WidgetTickReceiver : BroadcastReceiver() {

    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var batteryFilter: BatteryLevelFilter
    @Inject lateinit var redrawGate: RedrawGate
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)   // required by Hilt

        when (intent.action) {
            // Nothing is visible; stop drawing entirely.
            Intent.ACTION_SCREEN_OFF -> return

            Intent.ACTION_BATTERY_CHANGED -> {
                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                if (!batteryFilter.accept(level, scale)) return
            }

            // The screen coming back on, a time or zone change, or a locale-driven relayout can
            // all leave the launcher holding views we no longer agree with. Force the next draw
            // rather than trusting a cache that was populated before the change.
            Intent.ACTION_SCREEN_ON,
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> redrawGate.invalidateAll()
        }

        if (!widgetUpdater.hasWidgets()) return
        val pending = goAsync()
        scope.launch {
            try {
                widgetUpdater.refreshAll()
            } finally {
                pending.finish()
            }
        }
    }
}
