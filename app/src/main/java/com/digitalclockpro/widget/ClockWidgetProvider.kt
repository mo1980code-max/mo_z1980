package com.digitalclockpro.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.os.BatteryManager
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import com.digitalclockpro.R
import com.digitalclockpro.clockengine.RedrawGate
import com.digitalclockpro.clockengine.WidgetSignature
import com.digitalclockpro.clockengine.timeBucket
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.ClockStyle
import com.digitalclockpro.domain.model.WidgetConfig
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.repository.WeatherRepository
import com.digitalclockpro.domain.repository.WidgetConfigRepository
import com.digitalclockpro.domain.repository.WorldClockRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * The main resizable digital clock widget (2x1 … 5x2, fully resizable).
 *
 * Everything that cannot be expressed with RemoteViews (custom fonts, neon glow, gradients) is
 * rendered by [ClockWidgetRenderer] into an ImageView bitmap; everything else (background shape,
 * battery bar, tap regions) stays as real RemoteViews so the widget keeps launcher animations.
 */
@AndroidEntryPoint
open class ClockWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var configRepository: WidgetConfigRepository
    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var weatherRepository: WeatherRepository
    @Inject lateinit var worldClockRepository: WorldClockRepository
    @Inject lateinit var renderer: ClockWidgetRenderer
    @Inject lateinit var redrawGate: RedrawGate

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        // force = true: onUpdate fires after a reboot, a launcher restart, or first placement,
        // and in those cases the launcher is holding nothing for us. Honouring a stale cache
        // entry here would leave a blank box on the home screen.
        appWidgetIds.forEach { id -> render(context, manager, id, force = true) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        // Re-render on resize so the responsive text scaling kicks in. The size is part of the
        // signature, so force is belt and braces — but a resize must never be dropped.
        render(context, manager, appWidgetId, force = true)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppIntents.ACTION_WIDGET_REFRESH) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = intent.getIntArrayExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS)
                ?: manager.getAppWidgetIds(android.content.ComponentName(context, javaClass))
            // Our own refresh broadcast: this is the one path the gate is allowed to skip.
            ids.forEach { render(context, manager, it, force = false) }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        // Drop the cached signatures too, otherwise the map leaks one entry per removed widget
        // for the lifetime of the process.
        appWidgetIds.forEach { redrawGate.forget(it) }
        CoroutineScope(Dispatchers.IO).launch {
            appWidgetIds.forEach { configRepository.deleteConfig(it) }
        }
    }

    protected fun render(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        force: Boolean = false
    ) {
        val result = goAsyncSafe()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val config = configRepository.getConfig(appWidgetId)

                // Collect the inputs first and ask the gate before rasterising anything. The
                // repository reads above are cheap; the bitmap draw and the cross-process
                // RemoteViews marshal below are not, and they are what this skip avoids.
                val signature = signatureOf(context, manager, appWidgetId, config)
                if (!redrawGate.shouldRedraw(appWidgetId, signature, force)) return@launch

                val views = buildViews(context, manager, appWidgetId, config, signature)
                manager.updateAppWidget(appWidgetId, views)
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "render failed for $appWidgetId", t)
            } finally {
                result?.finish()
            }
        }
    }

    /**
     * Everything that can change the rendered pixels, folded into one comparable value.
     *
     * `config.hashCode()` stands in for the whole configuration: [WidgetConfig] is a data class,
     * so every colour, font, toggle and tap action is already covered and new fields are picked
     * up automatically instead of being silently forgotten here.
     */
    private suspend fun signatureOf(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        config: WidgetConfig
    ): WidgetSignature {
        val (widthDp, heightDp) = sizeOf(manager, appWidgetId)
        return WidgetSignature(
            configHash = config.hashCode(),
            timeBucket = timeBucket(
                java.time.Instant.now().epochSecond,
                showSeconds = config.showSeconds
            ),
            batteryPercent = if (config.showBattery) batteryPercent(context) else null,
            nextAlarmText = if (config.showNextAlarm) nextAlarmLabel(config) else null,
            weatherText = if (config.showWeather) weatherLabel(config) else null,
            widthDp = widthDp,
            heightDp = heightDp
        )
    }

    private fun sizeOf(manager: AppWidgetManager, appWidgetId: Int): Pair<Int, Int> {
        val options = manager.getAppWidgetOptions(appWidgetId)
        val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250)
            .coerceAtLeast(80)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 110)
            .coerceAtLeast(40)
        return widthDp to heightDp
    }

    private fun buildViews(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        config: WidgetConfig,
        signature: WidgetSignature
    ): RemoteViews {
        // Reuses the values already gathered for the signature instead of re-reading the
        // battery and re-querying alarms and weather a second time per draw.
        val widthDp = signature.widthDp
        val heightDp = signature.heightDp

        val now = ZonedDateTime.now()
        val battery = signature.batteryPercent
        val nextAlarm = signature.nextAlarmText
        val weather = signature.weatherText

        val payload = ClockWidgetRenderer.Payload(
            config = config,
            now = now,
            widthDp = widthDp,
            heightDp = (heightDp * 0.62f).toInt().coerceAtLeast(36),
            batteryPercent = battery,
            nextAlarmText = nextAlarm,
            weatherText = weather
        )

        return RemoteViews(context.packageName, R.layout.widget_clock).apply {
            // ---- background ----
            setInt(R.id.widget_root, "setBackgroundResource", backgroundFor(config.preset))
            setInt(
                R.id.widget_background, "setColorFilter",
                Color.argb(
                    config.backgroundAlpha,
                    Color.red(config.backgroundColor.toInt()),
                    Color.green(config.backgroundColor.toInt()),
                    Color.blue(config.backgroundColor.toInt())
                )
            )
            setInt(R.id.widget_background, "setImageAlpha", config.backgroundAlpha)

            // ---- clock + info bitmaps ----
            setImageViewBitmap(R.id.widget_time_image, renderer.renderTime(payload))
            renderer.renderInfoLine(payload)?.let {
                setImageViewBitmap(R.id.widget_info_image, it)
                setViewVisibility(R.id.widget_info_image, View.VISIBLE)
            } ?: setViewVisibility(R.id.widget_info_image, View.GONE)

            // ---- battery bar ----
            if (config.showBattery && config.batteryAsBar && battery != null) {
                setViewVisibility(R.id.widget_battery_bar, View.VISIBLE)
                setProgressBar(R.id.widget_battery_bar, 100, battery, false)
                setInt(R.id.widget_battery_bar, "setBackgroundColor", Color.TRANSPARENT)
            } else {
                setViewVisibility(R.id.widget_battery_bar, View.GONE)
            }

            // ---- tap regions (hours / minutes / date / weather) ----
            bindTap(context, appWidgetId, R.id.tap_hours, config.tapHours, WidgetTapActions.REGION_HOURS)
            bindTap(context, appWidgetId, R.id.tap_minutes, config.tapMinutes, WidgetTapActions.REGION_MINUTES)
            bindTap(context, appWidgetId, R.id.tap_date, config.tapDate, WidgetTapActions.REGION_DATE)
            bindTap(context, appWidgetId, R.id.tap_weather, config.tapWeather, WidgetTapActions.REGION_WEATHER)
        }
    }

    private fun RemoteViews.bindTap(
        context: Context,
        appWidgetId: Int,
        viewId: Int,
        action: com.digitalclockpro.domain.model.TapAction,
        region: Int
    ) {
        val pi: PendingIntent? = WidgetTapActions.pendingIntent(context, appWidgetId, action, region)
        setOnClickPendingIntent(viewId, pi)
    }

    private fun backgroundFor(style: ClockStyle): Int = when (style) {
        ClockStyle.GLASSMORPHISM -> R.drawable.widget_bg_glass
        ClockStyle.AMOLED_BLACK -> R.drawable.widget_bg_amoled
        ClockStyle.CYBERPUNK_NEON -> R.drawable.widget_bg_neon
        ClockStyle.RETRO_FLIP -> R.drawable.widget_bg_flip
        ClockStyle.SPLIT_FLAP -> R.drawable.widget_bg_splitflap
        ClockStyle.NIXIE_TUBE -> R.drawable.widget_bg_nixie
        ClockStyle.LCD_SEGMENT -> R.drawable.widget_bg_lcd
        ClockStyle.LED_MATRIX -> R.drawable.widget_bg_matrix
        ClockStyle.RETRO_TERMINAL -> R.drawable.widget_bg_terminal
        else -> R.drawable.widget_bg_default
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
        val next = java.time.Instant.ofEpochMilli(alarm.nextTriggerAtMillis())
            .atZone(java.time.ZoneId.systemDefault())
        return TimeFormatters.formatTime(next, config.use24Hour, showSeconds = false) +
            if (config.use24Hour) "" else " " + TimeFormatters.amPm(next)
    }

    private suspend fun weatherLabel(config: WidgetConfig): String? {
        val home = worldClockRepository.getSavedCities().firstOrNull { it.isHome }
            ?: worldClockRepository.getSavedCities().firstOrNull()
            ?: return null
        val snapshot = weatherRepository.current(home.latitude, home.longitude) ?: return null
        val unit = if (config.weatherUnitCelsius) "°C" else "°F"
        return "${snapshot.temperature(config.weatherUnitCelsius)}$unit ${snapshot.condition}"
    }

    /** `goAsync()` is only legal inside onReceive – guard it so direct calls do not crash. */
    private fun goAsyncSafe(): PendingResult? = runCatching { goAsync() }.getOrNull()

    private companion object { const val TAG = "ClockWidgetProvider" }
}
