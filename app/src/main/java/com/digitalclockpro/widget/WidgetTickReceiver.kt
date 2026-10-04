package com.digitalclockpro.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.digitalclockpro.di.ApplicationScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives the minute tick / screen / battery broadcasts and redraws the widgets.
 * Does *nothing* while the screen is off – ACTION_TIME_TICK is simply not delivered then,
 * which is exactly the battery behaviour we want.
 */
@AndroidEntryPoint
class WidgetTickReceiver : BroadcastReceiver() {

    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)   // required by Hilt
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> return   // stop drawing, nothing is visible
            else -> {
                if (!widgetUpdater.hasWidgets()) return
                val pending = goAsync()
                scope.launch {
                    try { widgetUpdater.refreshAll() } finally { pending.finish() }
                }
            }
        }
    }
}
