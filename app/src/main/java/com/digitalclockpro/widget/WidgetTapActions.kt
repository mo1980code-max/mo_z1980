package com.digitalclockpro.widget

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import android.provider.CalendarContract
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.domain.model.TapAction
import com.digitalclockpro.presentation.MainActivity
import com.digitalclockpro.presentation.studio.WidgetConfigActivity

/** Builds the PendingIntent for each configurable widget tap region. */
object WidgetTapActions {

    fun pendingIntent(context: Context, appWidgetId: Int, action: TapAction, region: Int): PendingIntent? {
        val intent = when (action) {
            TapAction.NONE -> return null
            TapAction.OPEN_APP -> main(context, "dashboard")
            TapAction.OPEN_ALARMS -> main(context, "alarms")
            TapAction.OPEN_WORLD_CLOCK -> main(context, "world")
            TapAction.OPEN_CALENDAR -> calendar()
            TapAction.OPEN_WEATHER -> weather(context) ?: main(context, "dashboard")
            TapAction.OPEN_WIDGET_SETTINGS -> WidgetConfigActivity.intent(context, appWidgetId)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return PendingIntent.getActivity(
            context,
            AppIntents.RC_WIDGET_BASE + appWidgetId * 10 + region,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun main(context: Context, destination: String) =
        Intent(context, MainActivity::class.java)
            .putExtra(AppIntents.EXTRA_START_DESTINATION, destination)

    private fun calendar(): Intent =
        Intent(Intent.ACTION_VIEW).setData(
            CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
                .appendPath(System.currentTimeMillis().toString()).build()
        )

    /** Prefers a installed weather app, falls back to the system clock's alarm screen. */
    private fun weather(context: Context): Intent? {
        val candidates = listOf(
            "com.google.android.apps.weather",
            "com.weather.Weather",
            "com.samsung.android.weather"
        )
        candidates.forEach { pkg ->
            context.packageManager.getLaunchIntentForPackage(pkg)?.let { return it }
        }
        return null
    }

    @Suppress("unused")
    fun systemAlarmApp(): Intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)

    @Suppress("unused")
    fun component(context: Context, cls: Class<*>) = ComponentName(context, cls)

    const val REGION_HOURS = 1
    const val REGION_MINUTES = 2
    const val REGION_DATE = 3
    const val REGION_WEATHER = 4
    const val REGION_ROOT = 5
}
