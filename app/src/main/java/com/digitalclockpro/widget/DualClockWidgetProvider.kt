package com.digitalclockpro.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.digitalclockpro.R
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.core.util.WorldClockLabels
import com.digitalclockpro.domain.model.TapAction
import com.digitalclockpro.domain.repository.WidgetConfigRepository
import com.digitalclockpro.domain.repository.WorldClockRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * 4x1 dual-zone widget: your own time on the left, one tracked city on the right.
 *
 * Deliberately *not* a two-row variant of [WorldClockWidgetProvider] — at 4x1 there is room for
 * exactly two columns, and the whole point of the format is an at-a-glance "here vs. there"
 * comparison with the offset spelled out between them.
 *
 * Which city it shows comes from `WidgetConfig.cityIds`, set in the Studio; if nothing is
 * configured it falls back to the first saved city that is not the user's home zone.
 */
@AndroidEntryPoint
class DualClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var configRepository: WidgetConfigRepository
    @Inject lateinit var worldClockRepository: WorldClockRepository

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
        // goAsync() keeps the broadcast alive while we hit Room/DataStore; without it the
        // process can be killed mid-query and the widget would keep its stale time.
        val pending = runCatching { goAsync() }.getOrNull()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = configRepository.getConfig(appWidgetId)
                val saved = worldClockRepository.getSavedCities()
                val now = ZonedDateTime.now()

                val homeCity = saved.firstOrNull { it.isHome }
                val homeZone = homeCity?.zone ?: ZoneId.systemDefault()

                val remote = config.cityIds
                    .firstNotNullOfOrNull { id -> saved.firstOrNull { it.id == id } }
                    ?: saved.firstOrNull { it.zoneId != homeZone.id }

                val views = RemoteViews(context.packageName, R.layout.widget_dual_clock).apply {
                    setInt(R.id.dual_background, "setImageAlpha", config.backgroundAlpha)

                    // ---- left: the user ----
                    setTextViewText(
                        R.id.dual_left_name,
                        homeCity?.cityName ?: context.getString(R.string.widget_dual_here)
                    )
                    setTextViewText(
                        R.id.dual_left_time,
                        TimeFormatters.formatTime(
                            now.withZoneSameInstant(homeZone), config.use24Hour, showSeconds = false
                        )
                    )
                    setTextColor(R.id.dual_left_time, config.timeColor.toInt())
                    setTextColor(R.id.dual_left_name, config.dateColor.toInt())

                    // ---- middle: the offset ----
                    setTextColor(R.id.dual_offset, config.accentColor.toInt())

                    // ---- right: the tracked city ----
                    if (remote == null) {
                        // No second city yet: say so instead of showing an empty half.
                        setTextViewText(
                            R.id.dual_right_name,
                            context.getString(R.string.widget_dual_pick_city)
                        )
                        setTextViewText(R.id.dual_right_time, "--:--")
                        setTextViewText(R.id.dual_offset, "")
                    } else {
                        setTextViewText(R.id.dual_right_name, remote.cityName)
                        setTextViewText(
                            R.id.dual_right_time,
                            TimeFormatters.formatTime(
                                remote.nowAt(now), config.use24Hour, showSeconds = false
                            )
                        )
                        setTextViewText(
                            R.id.dual_offset,
                            WorldClockLabels.shortOffset(remote.offsetMinutesFrom(homeZone, now))
                        )
                        setTextViewText(
                            R.id.dual_right_meta,
                            WorldClockLabels.dayOffset(context, remote, homeZone, now)
                        )
                    }
                    setTextColor(R.id.dual_right_time, config.timeColor.toInt())
                    setTextColor(R.id.dual_right_name, config.dateColor.toInt())
                    setTextColor(R.id.dual_right_meta, config.dateColor.toInt())

                    WidgetTapActions.pendingIntent(
                        context, appWidgetId, TapAction.OPEN_WORLD_CLOCK, WidgetTapActions.REGION_ROOT
                    )?.let { setOnClickPendingIntent(R.id.dual_root, it) }
                }
                manager.updateAppWidget(appWidgetId, views)
            } finally {
                pending?.finish()
            }
        }
    }
}
