package com.digitalclockpro.widget

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Battery policy for widget refreshes:
 *
 *  - While the screen is **ON** we listen to `ACTION_TIME_TICK` (a free, system-broadcast,
 *    once-per-minute signal; it is never delivered while the screen is off).
 *  - Seconds-enabled widgets additionally use `setChronometer`-style self-updating views instead
 *    of a repeating alarm, so we never hold a wake-lock.
 *  - While the screen is **OFF** the receiver is unregistered: zero CPU, zero wake-ups.
 *  - `ACTION_SCREEN_ON`, `TIME_SET`, `TIMEZONE_CHANGED`, `BATTERY_CHANGED` and
 *    `NEXT_ALARM_CLOCK_CHANGED` force an immediate redraw so the widget is never stale.
 *
 * Registered dynamically (not in the manifest) because implicit `TIME_TICK` cannot be declared
 * statically since Android 8.
 */
@Singleton
class WidgetTickController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val screenReceiver = WidgetTickReceiver()
    private var registered = false

    fun register() {
        if (registered) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                addAction(android.app.AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            }
        }
        ContextCompat.registerReceiver(
            context, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED
        )
        registered = true
    }

    fun unregister() {
        if (!registered) return
        runCatching { context.unregisterReceiver(screenReceiver) }
        registered = false
    }
}
