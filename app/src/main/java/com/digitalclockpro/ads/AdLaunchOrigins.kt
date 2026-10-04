package com.digitalclockpro.ads

import android.content.Intent
import com.digitalclockpro.clockengine.AdLaunchOrigin
import com.digitalclockpro.core.util.AppIntents

/**
 * Translates the `Intent` that opened [com.digitalclockpro.presentation.MainActivity] into
 * the plain [AdLaunchOrigin] consumed by [com.digitalclockpro.clockengine.AdPolicy].
 *
 * Parsing lives here because it needs `android.content.Intent`; the *decision* ("an alarm
 * open never sees an app-open ad") stays in the pure policy. The ALARM extra is set by:
 *  - [com.digitalclockpro.alarm.AlarmScheduler] on the status-bar alarm icon
 *    (`AlarmManager.setAlarmClock`'s show intent),
 *  - [com.digitalclockpro.presentation.ringing.AlarmRingingActivity], which reports itself
 *    when the full-screen intent fires.
 */
object AdLaunchOrigins {

    fun fromIntent(intent: Intent): AdLaunchOrigin = when {
        intent.getBooleanExtra(AppIntents.EXTRA_FROM_ALARM, false) -> AdLaunchOrigin.ALARM
        intent.getBooleanExtra(AppIntents.EXTRA_FROM_WIDGET, false) -> AdLaunchOrigin.WIDGET
        else -> AdLaunchOrigin.NORMAL
    }
}
