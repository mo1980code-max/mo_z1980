package com.digitalclockpro.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import com.digitalclockpro.R
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.WidgetConfig
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.repository.WidgetConfigRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import kotlin.math.min

/**
 * Resizable **analog** clock widget (3x3 and 4x4 defaults, resizable in between).
 *
 * Separate provider rather than a mode of [ClockWidgetProvider] because the launcher picker shows
 * one entry per provider: users expect to see "Analog Clock" next to "Digital Clock" rather than
 * having to place a digital widget and then convert it.
 *
 * Battery: `updatePeriodMillis="0"` like every other widget here — the dial is redrawn from
 * `ACTION_TIME_TICK` (once a minute, screen-on only) via `WidgetTickController`. The second hand
 * is therefore **not** animated on the home screen; it is drawn at the current second whenever a
 * redraw happens, and sweeping motion is reserved for the in-app and desk-clock dials.
 */
@AndroidEntryPoint
open class AnalogClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var configRepository: WidgetConfigRepository
    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var renderer: AnalogClockRenderer

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Re-rasterise at the new size so the dial never scales up blurrily.
        render(context, manager, appWidgetId)
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
                manager.updateAppWidget(appWidgetId, buildViews(context, manager, appWidgetId, config))
            } catch (t: Throwable) {
                Log.e(TAG, "analog render failed for $appWidgetId", t)
            } finally {
                pending?.finish()
            }
        }
    }

    private suspend fun buildViews(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        config: WidgetConfig
    ): RemoteViews {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 150)
            .coerceAtLeast(60)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 150)
            .coerceAtLeast(60)
        // A dial is always square: use the smaller edge so it never gets cropped.
        val side = min(widthDp, heightDp)

        val now = ZonedDateTime.now()
        val battery = if (config.showBattery) batteryPercent(context) else null
        val nextAlarm = if (config.showNextAlarm) nextAlarmLabel(config) else null

        val bitmap = renderer.render(
            AnalogClockRenderer.Payload(
                config = config,
                now = now,
                widthDp = side,
                heightDp = side,
                batteryPercent = battery,
                nextAlarmText = nextAlarm
            )
        )

        return RemoteViews(context.packageName, R.layout.widget_analog_clock).apply {
            setImageViewBitmap(R.id.analog_dial, bitmap)

            val caption = listOfNotNull(
                if (config.showDate) TimeFormatters.formatDate(now, config.datePattern) else null,
                nextAlarm?.let { context.getString(R.string.widget_next_alarm_prefix, it) },
                battery?.takeIf { config.showBattery && !config.batteryAsBar }
                    ?.let { context.getString(R.string.widget_battery_percent, it) }
            ).joinToString("  •  ")

            if (caption.isBlank()) {
                setViewVisibility(R.id.analog_caption, View.GONE)
            } else {
                setViewVisibility(R.id.analog_caption, View.VISIBLE)
                setTextViewText(R.id.analog_caption, caption)
                setTextColor(R.id.analog_caption, config.dateColor.toInt())
            }

            // Whole dial is one tap target: analog widgets have no hour/minute regions to split.
            setOnClickPendingIntent(
                R.id.analog_dial,
                WidgetTapActions.pendingIntent(
                    context, appWidgetId, config.tapHours, WidgetTapActions.REGION_HOURS
                )
            )
            setOnClickPendingIntent(
                R.id.analog_caption,
                WidgetTapActions.pendingIntent(
                    context, appWidgetId, config.tapDate, WidgetTapActions.REGION_DATE
                )
            )
        }
    }

    private fun batteryPercent(context: Context): Int? {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return null
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level < 0 || scale <= 0) null else (level * 100 / scale)
    }

    private suspend fun nextAlarmLabel(config: WidgetConfig): String? {
        val alarm = alarmRepository.getEnabledAlarms().minByOrNull { it.nextTriggerAtMillis() }
            ?: return null
        val next = Instant.ofEpochMilli(alarm.nextTriggerAtMillis()).atZone(ZoneId.systemDefault())
        return TimeFormatters.formatTime(next, config.use24Hour, showSeconds = false)
    }

    private companion object { const val TAG = "AnalogClockWidget" }
}

/**
 * Identical widget registered a second time with a 4x4 default placement.
 *
 * A launcher shows one entry per *provider*, and `targetCellWidth/Height` lives in the provider
 * metadata — so offering both a 3x3 and a 4x4 default requires two receivers. All behaviour is
 * inherited; only the `appwidget-provider` XML differs.
 */
@AndroidEntryPoint
class AnalogClockWidgetLargeProvider : AnalogClockWidgetProvider()
