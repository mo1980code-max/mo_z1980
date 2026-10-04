package com.digitalclockpro.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.digitalclockpro.R
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.SavedCity
import com.digitalclockpro.domain.model.TapAction
import com.digitalclockpro.domain.repository.WidgetConfigRepository
import com.digitalclockpro.domain.repository.WorldClockRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

/** Dual-zone / multi-city widget (up to 4 rows) for tracking colleagues and family abroad. */
@AndroidEntryPoint
class WorldClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var configRepository: WidgetConfigRepository
    @Inject lateinit var worldClockRepository: WorldClockRepository

    private val rowIds = intArrayOf(R.id.city_row_1, R.id.city_row_2, R.id.city_row_3, R.id.city_row_4)
    private val nameIds = intArrayOf(R.id.city_name_1, R.id.city_name_2, R.id.city_name_3, R.id.city_name_4)
    private val timeIds = intArrayOf(R.id.city_time_1, R.id.city_time_2, R.id.city_time_3, R.id.city_time_4)
    private val metaIds = intArrayOf(R.id.city_meta_1, R.id.city_meta_2, R.id.city_meta_3, R.id.city_meta_4)

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppIntents.ACTION_WIDGET_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: manager.getAppWidgetIds(ComponentName(context, javaClass))
            ids.forEach { render(context, manager, it) }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { configRepository.deleteConfig(it) }
        }
    }

    private fun render(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
        val pending = runCatching { goAsync() }.getOrNull()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = configRepository.getConfig(appWidgetId)
                val saved = worldClockRepository.getSavedCities()
                val cities = if (config.cityIds.isEmpty()) saved.take(4)
                else saved.filter { it.id in config.cityIds }.take(4)

                val now = ZonedDateTime.now()
                val homeZone = saved.firstOrNull { it.isHome }?.zone ?: java.time.ZoneId.systemDefault()

                val views = RemoteViews(context.packageName, R.layout.widget_world_clock).apply {
                    setTextColor(R.id.widget_world_title, config.accentColor.toInt())
                    setInt(R.id.widget_world_background, "setImageAlpha", config.backgroundAlpha)

                    rowIds.indices.forEach { index ->
                        val city: SavedCity? = cities.getOrNull(index)
                        if (city == null) {
                            setViewVisibility(rowIds[index], View.GONE)
                            return@forEach
                        }
                        val local = city.nowAt(now)
                        setViewVisibility(rowIds[index], View.VISIBLE)
                        setTextViewText(nameIds[index], city.cityName)
                        setTextColor(nameIds[index], config.dateColor.toInt())
                        setTextViewText(
                            timeIds[index],
                            TimeFormatters.formatTime(local, config.use24Hour, showSeconds = false)
                        )
                        setTextColor(timeIds[index], config.timeColor.toInt())
                        setTextViewText(
                            metaIds[index],
                            "${if (city.isDaytime(now)) "☀" else "☾"} ${city.utcOffsetLabel(now)} · ${city.dayLabel(homeZone, now)}"
                        )
                        setTextColor(metaIds[index], config.dateColor.toInt())
                    }

                    WidgetTapActions.pendingIntent(
                        context, appWidgetId, TapAction.OPEN_WORLD_CLOCK, WidgetTapActions.REGION_ROOT
                    )?.let { setOnClickPendingIntent(R.id.widget_world_root, it) }
                }
                manager.updateAppWidget(appWidgetId, views)
            } finally {
                pending?.finish()
            }
        }
    }
}
