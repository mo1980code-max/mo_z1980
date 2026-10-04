package com.digitalclockpro.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.ClockStyle
import com.digitalclockpro.domain.model.WidgetConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Renders the clock into a Bitmap so widgets can use **custom typefaces, neon glow, gradients and
 * drop shadows** – none of which RemoteViews TextView supports.
 *
 * Responsive scaling: the text size is derived from the real widget width/height reported by
 * `OPTION_APPWIDGET_MIN_WIDTH`, then auto-shrunk until it fits, which prevents clipping across
 * launchers and densities.
 */
@Singleton
class ClockWidgetRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Payload(
        val config: WidgetConfig,
        val now: ZonedDateTime,
        val widthDp: Int,
        val heightDp: Int,
        val batteryPercent: Int?,
        val nextAlarmText: String?,
        val weatherText: String?
    )

    fun renderTime(payload: Payload): Bitmap {
        val cfg = payload.config
        val widthPx = dp(payload.widthDp.coerceAtLeast(60))
        val heightPx = dp(payload.heightDp.coerceAtLeast(40))
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val timeText = TimeFormatters.formatTime(payload.now, cfg.use24Hour, cfg.showSeconds)
        val amPm = if (!cfg.use24Hour && cfg.showAmPm) TimeFormatters.amPm(payload.now) else ""

        val paint = basePaint(cfg).apply {
            typeface = resolveTypeface(cfg)
            textSize = fittingTextSize(timeText, this, widthPx * 0.94f, heightPx * 0.8f, cfg)
            letterSpacing = cfg.letterSpacing
        }
        applyEffects(paint, cfg, widthPx.toFloat(), paint.textSize)

        val metrics = paint.fontMetrics
        val baseline = heightPx / 2f - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(timeText, widthPx / 2f, baseline, paint)

        if (amPm.isNotEmpty()) {
            val amPaint = Paint(paint).apply {
                clearShadowLayer()
                maskFilter = null
                shader = null
                color = cfg.accentColor.toInt()
                textSize = paint.textSize * 0.26f
                textAlign = Paint.Align.LEFT
            }
            val timeWidth = paint.measureText(timeText)
            canvas.drawText(
                amPm,
                (widthPx + timeWidth) / 2f + dp(4),
                baseline - paint.textSize * 0.55f,
                amPaint
            )
        }
        return bitmap
    }

    /** Secondary line: date • battery • next alarm • weather. */
    fun renderInfoLine(payload: Payload): Bitmap? {
        val cfg = payload.config
        val parts = buildList {
            if (cfg.showDate) add(TimeFormatters.formatDate(payload.now, cfg.datePattern))
            if (cfg.showBattery && !cfg.batteryAsBar) payload.batteryPercent?.let { add("$it%") }
            if (cfg.showNextAlarm) payload.nextAlarmText?.let { add("⏰ $it") }
            if (cfg.showWeather) payload.weatherText?.let { add(it) }
        }
        if (parts.isEmpty()) return null

        val widthPx = dp(payload.widthDp.coerceAtLeast(60))
        val heightPx = dp(22)
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cfg.dateColor.toInt()
            textAlign = Paint.Align.CENTER
            typeface = if (cfg.preset == ClockStyle.MINIMAL_MONO) Typeface.MONOSPACE
            else resolveTypeface(cfg)
            textSize = spToPx(cfg.dateTextSizeSp)
        }
        var text = parts.joinToString("  •  ")
        while (paint.measureText(text) > widthPx * 0.96f && paint.textSize > spToPx(8f)) {
            paint.textSize -= 1f
        }
        if (paint.measureText(text) > widthPx * 0.96f && parts.size > 1) {
            text = parts.first()
        }
        val metrics = paint.fontMetrics
        canvas.drawText(text, widthPx / 2f, heightPx / 2f - (metrics.ascent + metrics.descent) / 2f, paint)
        return bitmap
    }

    private fun basePaint(cfg: WidgetConfig) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = cfg.timeColor.toInt()
        textAlign = Paint.Align.CENTER
        isSubpixelText = true
        isLinearText = true
    }

    private fun applyEffects(paint: Paint, cfg: WidgetConfig, width: Float, textSize: Float) {
        if (cfg.gradientEnabled) {
            paint.shader = LinearGradient(
                0f, 0f, width, textSize,
                cfg.timeColor.toInt(), cfg.gradientEndColor.toInt(),
                Shader.TileMode.CLAMP
            )
        }
        when {
            cfg.glowEnabled -> paint.setShadowLayer(
                dp(cfg.glowRadius.toInt()).toFloat().coerceAtLeast(1f),
                0f, 0f,
                withAlpha(cfg.timeColor.toInt(), 0.85f)
            )
            cfg.shadowEnabled -> paint.setShadowLayer(
                dp(cfg.shadowRadius.toInt()).toFloat().coerceAtLeast(1f),
                dp(cfg.shadowDx.toInt()).toFloat(),
                dp(cfg.shadowDy.toInt()).toFloat(),
                cfg.shadowColor.toInt()
            )
        }
        if (cfg.preset == ClockStyle.CYBERPUNK_NEON) {
            paint.maskFilter = BlurMaskFilter(dp(1).toFloat(), BlurMaskFilter.Blur.SOLID)
        }
    }

    private fun resolveTypeface(cfg: WidgetConfig): Typeface =
        FontCatalog.typeface(context, cfg.fontKey, cfg.customFontUri)

    /** Shrinks until the string fits both the available width and height. */
    private fun fittingTextSize(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxHeight: Float,
        cfg: WidgetConfig
    ): Float {
        var size = min(spToPx(cfg.timeTextSizeSp), maxHeight)
        paint.textSize = size
        while ((paint.measureText(text) > maxWidth || textHeight(paint) > maxHeight) && size > spToPx(10f)) {
            size -= 1f
            paint.textSize = size
        }
        return size
    }

    private fun textHeight(paint: Paint): Float = paint.fontMetrics.run { descent - ascent }

    private fun withAlpha(color: Int, factor: Float) = Color.argb(
        (255 * factor).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color)
    )

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics
    ).toInt().coerceAtLeast(1)

    private fun spToPx(sp: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_SP, sp, context.resources.displayMetrics
    )
}
