package com.digitalclockpro.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.util.TypedValue
import com.digitalclockpro.clockengine.ClockEngine
import com.digitalclockpro.clockengine.LocaleText
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.AnalogFace
import com.digitalclockpro.domain.model.WidgetConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Draws an analog dial into a Bitmap for the widget, mirroring [ClockWidgetRenderer]'s approach:
 * RemoteViews cannot draw arbitrary vector content, so the whole face is rasterised and pushed
 * into a single ImageView.
 *
 * All geometry comes from the pure [ClockEngine], so the widget and the in-app Compose dial are
 * guaranteed to agree — and the maths is unit-tested without an emulator.
 *
 * Everything is expressed as a fraction of the dial radius, which makes the face resolution- and
 * size-independent: the same code renders a 3x3 and a 4x4 widget with no magic numbers per size.
 */
@Singleton
class AnalogClockRenderer @Inject constructor(
    @ApplicationContext private val context: Context
) {

    data class Payload(
        val config: WidgetConfig,
        val now: ZonedDateTime,
        val widthDp: Int,
        val heightDp: Int,
        val batteryPercent: Int? = null,
        val nextAlarmText: String? = null
    )

    fun render(payload: Payload): Bitmap {
        val cfg = payload.config
        val face = cfg.analogFace

        val sizePx = dp(min(payload.widthDp, payload.heightDp).coerceIn(60, 400))
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val center = sizePx / 2f
        // Leave room for the glow so a neon face is never clipped at the bitmap edge.
        val radius = center * if (face.glowRadius > 0f) 0.88f else 0.94f

        drawDial(canvas, face, cfg, center, radius)
        if (face.gearDecoration) drawGears(canvas, face, center, radius)
        drawTicks(canvas, face, cfg, center, radius)
        if (cfg.showAnalogNumerals) drawNumerals(canvas, face, cfg, center, radius)
        if (face.digitalInset) drawDigitalInset(canvas, face, cfg, payload, center, radius)

        drawHands(canvas, face, cfg, payload.now, center, radius)
        drawCapCircle(canvas, face, cfg, center, radius)

        return bitmap
    }

    // ------------------------------------------------------------------ dial

    private fun drawDial(
        canvas: Canvas,
        face: AnalogFace,
        cfg: WidgetConfig,
        center: Float,
        radius: Float
    ) {
        val dialPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(face.dialColor)
            // A transparent widget background must still show the dial, so alpha is applied
            // only when the user explicitly dialled the background down.
            alpha = cfg.backgroundAlpha.coerceIn(0, 255)
            if (face.metallicSheen) {
                shader = RadialGradient(
                    center - radius * 0.3f,
                    center - radius * 0.3f,
                    radius * 1.6f,
                    intArrayOf(lighten(argb(face.dialColor), 0.35f), argb(face.dialColor)),
                    floatArrayOf(0f, 1f),
                    Shader.TileMode.CLAMP
                )
            }
        }
        canvas.drawCircle(center, center, radius, dialPaint)

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.055f
            color = argb(if (cfg.analogColorsOverridden) cfg.accentColor else face.rimColor)
            if (face.metallicSheen) {
                // Sweep gradient = cheap brushed-metal bezel without any asset.
                shader = SweepGradient(
                    center, center,
                    intArrayOf(
                        argb(face.rimColor),
                        lighten(argb(face.rimColor), 0.55f),
                        argb(face.rimColor),
                        lighten(argb(face.rimColor), 0.3f),
                        argb(face.rimColor)
                    ),
                    null
                )
            }
            applyGlow(this, face, cfg)
        }
        canvas.drawCircle(center, center, radius - rimPaint.strokeWidth / 2f, rimPaint)
    }

    private fun drawGears(canvas: Canvas, face: AnalogFace, center: Float, radius: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.02f
            color = argb(face.tickColor)
            alpha = 70
        }
        // Two offset toothed rings, drawn as spokes — readable at widget size, costs no assets.
        listOf(
            Triple(center - radius * 0.32f, center + radius * 0.18f, radius * 0.26f),
            Triple(center + radius * 0.34f, center - radius * 0.22f, radius * 0.19f)
        ).forEach { (cx, cy, r) ->
            canvas.drawCircle(cx, cy, r, paint)
            canvas.drawCircle(cx, cy, r * 0.55f, paint)
            val teeth = 12
            for (i in 0 until teeth) {
                val angle = 360f / teeth * i
                val inner = ClockEngine.pointOnDial(cx, cy, r, angle)
                val outer = ClockEngine.pointOnDial(cx, cy, r * 1.18f, angle)
                canvas.drawLine(inner.x, inner.y, outer.x, outer.y, paint)
            }
        }
    }

    // ------------------------------------------------------------------ ticks & numerals

    private fun drawTicks(
        canvas: Canvas,
        face: AnalogFace,
        cfg: WidgetConfig,
        center: Float,
        radius: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(if (cfg.analogColorsOverridden) cfg.timeColor else face.tickColor)
            strokeCap = Paint.Cap.ROUND
            applyGlow(this, face, cfg)
        }

        for (index in 0 until TICK_COUNT) {
            val major = ClockEngine.isMajorTick(index, TICK_COUNT)
            if (!major && !face.minuteTicks) continue

            paint.strokeWidth = if (major) radius * 0.035f else radius * 0.014f
            val innerRadius = radius * if (major) 0.80f else 0.86f
            val outerRadius = radius * 0.91f
            val angle = ClockEngine.tickAngle(index, TICK_COUNT)
            val inner = ClockEngine.pointOnDial(center, center, innerRadius, angle)
            val outer = ClockEngine.pointOnDial(center, center, outerRadius, angle)
            canvas.drawLine(inner.x, inner.y, outer.x, outer.y, paint)
        }
    }

    private fun drawNumerals(
        canvas: Canvas,
        face: AnalogFace,
        cfg: WidgetConfig,
        center: Float,
        radius: Float
    ) {
        if (face.numerals == AnalogFace.NumeralStyle.NONE) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(if (cfg.analogColorsOverridden) cfg.timeColor else face.numeralColor)
            textAlign = Paint.Align.CENTER
            textSize = radius * if (face.numerals == AnalogFace.NumeralStyle.ROMAN) 0.17f else 0.21f
            isFakeBoldText = face.numerals != AnalogFace.NumeralStyle.ROMAN
            applyGlow(this, face, cfg)
        }

        val numeralRadius = radius * 0.66f
        val locale = Locale.getDefault()
        for (hour in 1..12) {
            val label = when (face.numerals) {
                AnalogFace.NumeralStyle.ROMAN -> ClockEngine.romanNumeral(hour)
                // "Arabic numerals" here means the Western 1-12 shapes, not the Arabic language.
                // Localising them keeps the dial consistent with the digital readout, which goes
                // through DateTimeFormatter and already renders ٠٩:٤١ under an Arabic locale.
                // A face showing 1..12 next to a clock showing ٠٩:٤١ looked like two apps.
                AnalogFace.NumeralStyle.ARABIC -> LocaleText.localizeDigits(hour, locale)
                AnalogFace.NumeralStyle.ARABIC_QUARTERS ->
                    if (hour % 3 == 0) LocaleText.localizeDigits(hour, locale) else null
                AnalogFace.NumeralStyle.NONE -> null
            } ?: continue

            val point = ClockEngine.pointOnDial(
                center, center, numeralRadius, hour * ClockEngine.DEGREES_PER_HOUR
            )
            // Vertically centre the glyph on the point (baseline sits below the visual centre).
            val baselineShift = (paint.descent() + paint.ascent()) / 2f
            canvas.drawText(label, point.x, point.y - baselineShift, paint)
        }
    }

    private fun drawDigitalInset(
        canvas: Canvas,
        face: AnalogFace,
        cfg: WidgetConfig,
        payload: Payload,
        center: Float,
        radius: Float
    ) {
        val timeText = TimeFormatters.formatTime(payload.now, cfg.use24Hour, showSeconds = false)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(if (cfg.analogColorsOverridden) cfg.accentColor else face.secondHandColor)
            textAlign = Paint.Align.CENTER
            textSize = radius * 0.17f
            isFakeBoldText = true
        }
        val boxTop = center + radius * 0.26f
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            alpha = 90
        }
        canvas.drawRoundRect(
            center - radius * 0.34f, boxTop,
            center + radius * 0.34f, boxTop + radius * 0.26f,
            radius * 0.05f, radius * 0.05f, boxPaint
        )
        canvas.drawText(timeText, center, boxTop + radius * 0.2f, paint)
    }

    // ------------------------------------------------------------------ hands

    private fun drawHands(
        canvas: Canvas,
        face: AnalogFace,
        cfg: WidgetConfig,
        now: ZonedDateTime,
        center: Float,
        radius: Float
    ) {
        // Widgets are always quartz: a sweeping hand would need a redraw every 16 ms, which is
        // exactly the battery drain this project refuses to pay.
        val angles = ClockEngine.handAngles(
            hour = now.hour,
            minute = now.minute,
            second = now.second,
            nanosecond = now.nano,
            motion = ClockEngine.HandMotion.QUARTZ
        )

        drawHand(
            canvas, face, cfg, center, radius,
            degrees = angles.hourDegrees,
            length = radius * 0.52f,
            width = radius * 0.075f,
            color = if (cfg.analogColorsOverridden) cfg.timeColor else face.hourHandColor
        )
        drawHand(
            canvas, face, cfg, center, radius,
            degrees = angles.minuteDegrees,
            length = radius * 0.76f,
            width = radius * 0.05f,
            color = if (cfg.analogColorsOverridden) cfg.dateColor else face.minuteHandColor
        )
        if (cfg.showSecondHand) {
            drawHand(
                canvas, face, cfg, center, radius,
                degrees = angles.secondDegrees,
                length = radius * 0.84f,
                width = radius * 0.018f,
                color = if (cfg.analogColorsOverridden) cfg.accentColor else face.secondHandColor,
                style = AnalogFace.HandStyle.BATON,
                tailLength = radius * 0.2f
            )
        }
    }

    private fun drawHand(
        canvas: Canvas,
        face: AnalogFace,
        cfg: WidgetConfig,
        center: Float,
        radius: Float,
        degrees: Float,
        length: Float,
        width: Float,
        color: Long,
        style: AnalogFace.HandStyle = face.handStyle,
        tailLength: Float = radius * 0.12f
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = argb(color)
            strokeCap = Paint.Cap.ROUND
            applyGlow(this, face, cfg)
        }

        val tip = ClockEngine.pointOnDial(center, center, length, degrees)
        val tail = ClockEngine.pointOnDial(center, center, -tailLength, degrees)

        when (style) {
            AnalogFace.HandStyle.BATON -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = width
                canvas.drawLine(tail.x, tail.y, tip.x, tip.y, paint)
            }

            AnalogFace.HandStyle.LEAF -> {
                // Tapered diamond: widest a third of the way along the hand.
                val left = ClockEngine.pointOnDial(center, center, width * 1.6f, degrees - 90f)
                val right = ClockEngine.pointOnDial(center, center, width * 1.6f, degrees + 90f)
                val belly = ClockEngine.pointOnDial(center, center, length * 0.35f, degrees)
                val bellyLeft = ClockEngine.pointOnDial(belly.x, belly.y, width * 1.3f, degrees - 90f)
                val bellyRight = ClockEngine.pointOnDial(belly.x, belly.y, width * 1.3f, degrees + 90f)
                paint.style = Paint.Style.FILL
                canvas.drawPath(
                    Path().apply {
                        moveTo(tail.x, tail.y)
                        lineTo(left.x, left.y)
                        lineTo(bellyLeft.x, bellyLeft.y)
                        lineTo(tip.x, tip.y)
                        lineTo(bellyRight.x, bellyRight.y)
                        lineTo(right.x, right.y)
                        close()
                    },
                    paint
                )
            }

            AnalogFace.HandStyle.SKELETON -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = width * 0.45f
                val shoulder = ClockEngine.pointOnDial(center, center, length * 0.42f, degrees)
                val leftEdge = ClockEngine.pointOnDial(shoulder.x, shoulder.y, width, degrees - 90f)
                val rightEdge = ClockEngine.pointOnDial(shoulder.x, shoulder.y, width, degrees + 90f)
                canvas.drawPath(
                    Path().apply {
                        moveTo(tail.x, tail.y)
                        lineTo(leftEdge.x, leftEdge.y)
                        lineTo(tip.x, tip.y)
                        lineTo(rightEdge.x, rightEdge.y)
                        close()
                    },
                    paint
                )
            }
        }
    }

    private fun drawCapCircle(
        canvas: Canvas,
        face: AnalogFace,
        cfg: WidgetConfig,
        center: Float,
        radius: Float
    ) {
        val capPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = argb(if (cfg.analogColorsOverridden) cfg.accentColor else face.secondHandColor)
            applyGlow(this, face, cfg)
        }
        canvas.drawCircle(center, center, radius * 0.045f, capPaint)
        canvas.drawCircle(
            center, center, radius * 0.018f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb(face.dialColor) }
        )
    }

    // ------------------------------------------------------------------ helpers

    private fun applyGlow(paint: Paint, face: AnalogFace, cfg: WidgetConfig) {
        val radius = if (cfg.glowEnabled && face.glowRadius > 0f) face.glowRadius else return
        // BlurMaskFilter needs software rendering; widget bitmaps already are, so this is safe.
        paint.maskFilter = BlurMaskFilter(radius, BlurMaskFilter.Blur.SOLID)
    }

    private fun lighten(color: Int, fraction: Float): Int {
        fun channel(value: Int) = (value + (255 - value) * fraction).toInt().coerceIn(0, 255)
        return Color.argb(
            Color.alpha(color),
            channel(Color.red(color)),
            channel(Color.green(color)),
            channel(Color.blue(color))
        )
    }

    private fun argb(value: Long): Int = value.toInt()

    private fun dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        context.resources.displayMetrics
    ).toInt().coerceAtLeast(1)

    private companion object {
        const val TICK_COUNT = 60
    }
}
