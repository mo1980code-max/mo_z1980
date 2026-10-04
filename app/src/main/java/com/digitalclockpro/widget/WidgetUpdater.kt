package com.digitalclockpro.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.digitalclockpro.core.util.AppIntents
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Single place that pokes every placed widget (digital, analog, world clock and Glance). */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appWidgetManager: AppWidgetManager
) {

    private val providers = listOf(
        ClockWidgetProvider::class.java,
        AnalogClockWidgetProvider::class.java,
        AnalogClockWidgetLargeProvider::class.java,
        WorldClockWidgetProvider::class.java
    )

    fun refreshAll() {
        providers.forEach { cls ->
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return@forEach
            context.sendBroadcast(
                Intent(context, cls)
                    .setAction(AppIntents.ACTION_WIDGET_REFRESH)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }

    /**
     * Refreshes a single widget. The provider class is resolved from the id rather than assumed,
     * so saving an analog widget in the Studio updates the analog receiver and not the digital one.
     */
    fun refresh(appWidgetId: Int) {
        val target = providers.firstOrNull { cls ->
            appWidgetId in appWidgetManager.getAppWidgetIds(ComponentName(context, cls))
        } ?: ClockWidgetProvider::class.java

        context.sendBroadcast(
            Intent(context, target)
                .setAction(AppIntents.ACTION_WIDGET_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        )
    }

    fun hasWidgets(): Boolean = providers.any {
        appWidgetManager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
    }
}
