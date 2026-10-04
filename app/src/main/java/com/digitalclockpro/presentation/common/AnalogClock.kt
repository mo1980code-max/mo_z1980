package com.digitalclockpro.presentation.common

import com.digitalclockpro.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.digitalclockpro.clockengine.ClockEngine
import com.digitalclockpro.clockengine.LocaleText
import com.digitalclockpro.domain.model.AnalogFace
import com.digitalclockpro.domain.model.HandMotion
import java.time.LocalTime
import kotlin.math.min
import java.util.Locale

/**
 * In-app analog dial drawn with Compose `Canvas`.
 *
 * Shares every angle with the widget renderer through the pure [ClockEngine], so the two never
 * drift apart. The only intentional difference is motion: on screen we can afford
 * [HandMotion.SMOOTH] (a 60 fps sweep), which a home-screen widget must never do.
 */
@Composable
fun AnalogClock(
    face: AnalogFace,
    modifier: Modifier = Modifier,
    motion: HandMotion = HandMotion.QUARTZ,
    showSeconds: Boolean = true,
    showNumerals: Boolean = true,
    /** Overrides the face palette (used by the Studio colour tab); null keeps the face defaults. */
    accentOverride: Color? = null,
    /** Small digital readout for the Digital Hybrid face. */
    digitalInsetText: String? = null
) {
    val time = rememberTickingTime(motion)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // A Canvas is an empty rectangle to TalkBack: hands and numerals are pixels, not nodes, so
    // the dial announced nothing at all. Speak the time it is showing instead of describing the
    // drawing — "analog clock showing 9:41" is what a sighted user actually gets from it.
    // Recomputed only when the displayed minute changes, not on every sweep frame.
    val spokenTime = remember(time.hour, time.minute) {
        "%d:%02d".format(
            if (time.hour % 12 == 0) 12 else time.hour % 12,
            time.minute
        )
    }
    val description = stringResource(R.string.analog_clock_description, spokenTime)

    Canvas(
        modifier.semantics {
            contentDescription = description
            // Announced as an image, not a button: nothing here is actionable.
            role = Role.Image
        }
    ) {
        val radius = min(size.width, size.height) / 2f * 0.95f
        val centerX = size.width / 2f
        val centerY = size.height / 2f

        drawDial(face, centerX, centerY, radius, accentOverride)
        drawTicks(face, centerX, centerY, radius)
        if (showNumerals) {
            drawNumerals(face, centerX, centerY, radius, textMeasurer, density.density)
        }
        if (face.digitalInset && digitalInsetText != null) {
            drawDigitalInset(face, centerX, centerY, radius, digitalInsetText, textMeasurer)
        }

        val angles = ClockEngine.handAngles(
            hour = time.hour,
            minute = time.minute,
            second = time.second,
            nanosecond = time.nano,
            motion = motion.toEngine()
        )
        drawHand(face, centerX, centerY, radius, angles.hourDegrees, 0.52f, 0.075f, Color(face.hourHandColor))
        drawHand(face, centerX, centerY, radius, angles.minuteDegrees, 0.76f, 0.05f, Color(face.minuteHandColor))
        if (showSeconds) {
            drawHand(
                face, centerX, centerY, radius, angles.secondDegrees, 0.84f, 0.018f,
                accentOverride ?: Color(face.secondHandColor),
                style = AnalogFace.HandStyle.BATON
            )
        }

        // Centre cap, drawn last so it sits on top of all three hands.
        drawCircle(
            color = accentOverride ?: Color(face.secondHandColor),
            radius = radius * 0.045f,
            center = Offset(centerX, centerY)
        )
        drawCircle(
            color = Color(face.dialColor),
            radius = radius * 0.018f,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Ticks at the cadence the motion requires: once a second for quartz, every frame for a sweep.
 *
 * `withFrameMillis` means the smooth variant is driven by the Choreographer and automatically
 * stops when the composable leaves the screen — no timer to leak.
 */
@Composable
private fun rememberTickingTime(motion: HandMotion): LocalTime {
    var time by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(motion) {
        if (motion == HandMotion.SMOOTH) {
            while (true) {
                withFrameMillis { }
                time = LocalTime.now()
            }
        } else {
            while (true) {
                time = LocalTime.now()
                // Sleep to the next whole second so the hand jumps exactly on the tick.
                val now = LocalTime.now()
                kotlinx.coroutines.delay(1_000L - (now.nano / 1_000_000L))
            }
        }
    }
    return time
}

// ---------------------------------------------------------------------- drawing

private fun DrawScope.drawDial(
    face: AnalogFace,
    centerX: Float,
    centerY: Float,
    radius: Float,
    accentOverride: Color?
) {
    if (face.metallicSheen) {
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(face.dialColor).lighten(0.35f),
                    Color(face.dialColor)
                ),
                center = Offset(centerX - radius * 0.3f, centerY - radius * 0.3f),
                radius = radius * 1.6f
            ),
            radius = radius,
            center = Offset(centerX, centerY)
        )
    } else {
        drawCircle(Color(face.dialColor), radius, Offset(centerX, centerY))
    }

    val rimWidth = radius * 0.055f
    drawCircle(
        color = accentOverride ?: Color(face.rimColor),
        radius = radius - rimWidth / 2f,
        center = Offset(centerX, centerY),
        style = Stroke(width = rimWidth)
    )
}

private fun DrawScope.drawTicks(
    face: AnalogFace,
    centerX: Float,
    centerY: Float,
    radius: Float
) {
    for (index in 0 until 60) {
        val major = ClockEngine.isMajorTick(index, 60)
        if (!major && !face.minuteTicks) continue

        val angle = ClockEngine.tickAngle(index, 60)
        val inner = ClockEngine.pointOnDial(
            centerX, centerY, radius * if (major) 0.80f else 0.86f, angle
        )
        val outer = ClockEngine.pointOnDial(centerX, centerY, radius * 0.91f, angle)
        drawLine(
            color = Color(face.tickColor),
            start = Offset(inner.x, inner.y),
            end = Offset(outer.x, outer.y),
            strokeWidth = if (major) radius * 0.035f else radius * 0.014f,
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawNumerals(
    face: AnalogFace,
    centerX: Float,
    centerY: Float,
    radius: Float,
    textMeasurer: TextMeasurer,
    density: Float
) {
    if (face.numerals == AnalogFace.NumeralStyle.NONE) return

    val fontSizePx = radius * if (face.numerals == AnalogFace.NumeralStyle.ROMAN) 0.17f else 0.21f
    val style = TextStyle(
        color = Color(face.numeralColor),
        fontSize = (fontSizePx / density).sp
    )

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

        val layout = textMeasurer.measure(label, style)
        val point = ClockEngine.pointOnDial(
            centerX, centerY, radius * 0.66f, hour * ClockEngine.DEGREES_PER_HOUR
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(
                point.x - layout.size.width / 2f,
                point.y - layout.size.height / 2f
            )
        )
    }
}

private fun DrawScope.drawDigitalInset(
    face: AnalogFace,
    centerX: Float,
    centerY: Float,
    radius: Float,
    text: String,
    textMeasurer: TextMeasurer
) {
    val layout = textMeasurer.measure(
        text,
        TextStyle(color = Color(face.secondHandColor), fontSize = (radius * 0.11f).sp)
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(centerX - layout.size.width / 2f, centerY + radius * 0.3f)
    )
}

private fun DrawScope.drawHand(
    face: AnalogFace,
    centerX: Float,
    centerY: Float,
    radius: Float,
    degrees: Float,
    lengthFraction: Float,
    widthFraction: Float,
    color: Color,
    style: AnalogFace.HandStyle = face.handStyle
) {
    val length = radius * lengthFraction
    val width = radius * widthFraction
    val tip = ClockEngine.pointOnDial(centerX, centerY, length, degrees)
    val tail = ClockEngine.pointOnDial(centerX, centerY, -radius * 0.12f, degrees)

    when (style) {
        AnalogFace.HandStyle.BATON -> drawLine(
            color = color,
            start = Offset(tail.x, tail.y),
            end = Offset(tip.x, tip.y),
            strokeWidth = width,
            cap = StrokeCap.Round
        )

        AnalogFace.HandStyle.LEAF, AnalogFace.HandStyle.SKELETON -> {
            val belly = ClockEngine.pointOnDial(centerX, centerY, length * 0.35f, degrees)
            val left = ClockEngine.pointOnDial(belly.x, belly.y, width * 1.3f, degrees - 90f)
            val right = ClockEngine.pointOnDial(belly.x, belly.y, width * 1.3f, degrees + 90f)
            val path = Path().apply {
                moveTo(tail.x, tail.y)
                lineTo(left.x, left.y)
                lineTo(tip.x, tip.y)
                lineTo(right.x, right.y)
                close()
            }
            if (style == AnalogFace.HandStyle.SKELETON) {
                drawPath(path, color, style = Stroke(width = width * 0.45f))
            } else {
                drawPath(path, color)
            }
        }
    }
}

private fun Color.lighten(fraction: Float): Color = Color(
    red = red + (1f - red) * fraction,
    green = green + (1f - green) * fraction,
    blue = blue + (1f - blue) * fraction,
    alpha = alpha
)

/** Compose has no public `DrawScope.drawText(TextLayoutResult)` on older BOMs; this bridges it. */
private fun DrawScope.drawText(
    textLayoutResult: androidx.compose.ui.text.TextLayoutResult,
    topLeft: Offset
) {
    drawContext.canvas.nativeCanvas.save()
    drawContext.canvas.nativeCanvas.translate(topLeft.x, topLeft.y)
    textLayoutResult.multiParagraph.paint(drawContext.canvas)
    drawContext.canvas.nativeCanvas.restore()
}
