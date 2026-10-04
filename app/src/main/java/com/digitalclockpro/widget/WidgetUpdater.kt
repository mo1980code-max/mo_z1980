package com.digitalclockpro.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import com.digitalclockpro.core.util.AppIntents
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Single place that pokes every placed widget (clock, world clock and Glance). */
@Singleton
class WidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appWidgetManager: AppWidgetManager
) {

    fun refreshAll() {
        listOf(ClockWidgetProvider::class.java, WorldClockWidgetProvider::class.java).forEach { cls ->
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, cls))
            if (ids.isEmpty()) return@forEach
            context.sendBroadcast(
                Intent(context, cls)
                    .setAction(AppIntents.ACTION_WIDGET_REFRESH)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            )
        }
    }

    fun refresh(appWidgetId: Int) {
        context.sendBroadcast(
            Intent(context, ClockWidgetProvider::class.java)
                .setAction(AppIntents.ACTION_WIDGET_REFRESH)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
        )
    }

    fun hasWidgets(): Boolean =
        listOf(ClockWidgetProvider::class.java, WorldClockWidgetProvider::class.java).any {
            appWidgetManager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty()
        }
}
