package com.digitalclockpro.core.util

object AppIntents {
    const val ACTION_ALARM_FIRE = "com.digitalclockpro.action.ALARM_FIRE"
    const val ACTION_ALARM_SNOOZE = "com.digitalclockpro.action.ALARM_SNOOZE"
    const val ACTION_ALARM_DISMISS = "com.digitalclockpro.action.ALARM_DISMISS"
    const val ACTION_WIDGET_REFRESH = "com.digitalclockpro.action.WIDGET_REFRESH"
    const val ACTION_WIDGET_TAP = "com.digitalclockpro.action.WIDGET_TAP"

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_IS_SNOOZE = "extra_is_snooze"
    const val EXTRA_TAP_TARGET = "extra_tap_target"
    const val EXTRA_WIDGET_ID = "extra_widget_id"
    const val EXTRA_START_DESTINATION = "extra_start_destination"

    /**
     * Ad gating (see the pure AdPolicy in :clock-engine). Set by the ALARM surfaces that
     * open MainActivity (status-bar alarm icon) and by widget tap actions, so the app-open
     * ad can be suppressed for alarm-originated opens without guessing from the destination.
     */
    const val EXTRA_FROM_ALARM = "extra_from_alarm"
    const val EXTRA_FROM_WIDGET = "extra_from_widget"

    /** PendingIntent request-code namespaces (avoids collisions between alarm ids and widgets). */
    const val RC_ALARM_BASE = 100_000
    const val RC_SNOOZE_BASE = 200_000
    const val RC_DISMISS_BASE = 300_000
    const val RC_FULLSCREEN_BASE = 400_000
    const val RC_WIDGET_BASE = 500_000
}
