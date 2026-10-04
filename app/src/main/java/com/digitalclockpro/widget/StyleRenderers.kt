package com.digitalclockpro.widget

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.digitalclockpro.clockengine.DotMatrixFont
import com.digitalclockpro.clockengine.LcdGrid
import com.digitalclockpro.clockengine.NixieGlow
import com.digitalclockpro.clockengine.Scanlines
import com.digitalclockpro.clockengine.SplitFlap
import com.digitalclockpro.domain.model.WidgetConfig

/**
 * Canvas painters for the five decorative digital styles added in Phase 2.
 *
 * Each one receives an already-sized canvas and is responsible for the *whole* time area, which
 * is why they are kept apart from [ClockWidgetRenderer]'s generic text path: a split-flap board
 * and an LED panel have nothing in common with "draw a string with a glow".
 *
 * All the arithmetic (cell widths, dot grids, scanline positions, glow passes) comes from the
 * pure `:clock-engine` module, so what lives here is only drawing.
 */
internal object StyleRenderers {

    // ------------------------------------------------------------------ Split-Flap

    /**
     * Airport departure board. Every character sits on its own card with a hard horizontal seam
     * across the middle — that seam is the whole illusion, so it is drawn as an explicit dark
     * line plus a subtle highlight on the lower half rather than left to a gradient.
     */
    fun drawSplitFlap(
        canvas: Canvas,
        text: String,
        cfg: WidgetConfig,
        width: Float,
        height: Float,
        typeface: Typeface,
        density: Float
    ) {
        val gap = 3f * density
        val padding = 4f * density
        val cells = SplitFlap.layout(text, width - padding * 2, gap)
        if (cells.isEmpty()) return

        val cardTop = padding
        val cardHeight = height - padding * 2
        val radius = 3f * density

        val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val seamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(200, 0, 0, 0)
            strokeWidth = (1.5f * density).coerceAtLeast(1f)
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            color = cfg.timeColor.toInt()
            textAlign = Paint.Align.CENTER
        }

        for (cell in cells) {
            val left = padding + cell.left
            val rect = RectF(left, cardTop, left + cell.width, cardTop + cardHeight)

            if (!cell.isSeparator) {
                // Upper half slightly lighter than the lower half: that is how a real flap looks
                // under the board's own downlight.
                cardPaint.shader = LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    intArrayOf(
                        lighten(cfg.backgroundColor.toInt(), 0.28f),
                        cfg.backgroundColor.toInt(),
                        lighten(cfg.backgroundColor.toInt(), 0.10f),
                        darken(cfg.backgroundColor.toInt(), 0.25f)
                    ),
                    floatArrayOf(0f, 0.49f, 0.51f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, radius, radius, cardPaint)
                cardPaint.shader = null

                val seamY = rect.top + cardHeight * SplitFlap.HINGE_FRACTION
                canvas.drawLine(rect.left, seamY, rect.right, seamY, seamPaint)
            }

            textPaint.textSize = fitInto(
                textPaint, cell.char.toString(),
                maxWidth = cell.width * 0.86f,
                maxHeight = cardHeight * 0.74f
            )
            val metrics = textPaint.fontMetrics
            canvas.drawText(
                cell.char.toString(),
                rect.centerX(),
                rect.centerY() - (metrics.ascent + metrics.descent) / 2f,
                textPaint
            )
        }
    }

    // ------------------------------------------------------------------ Nixie tubes

    /**
     * Nixie tubes. The orange cathode haze is built from several blurred passes (widest and
     * faintest first) because a single shadow layer reads as a flat drop shadow, not as light
     * trapped in glass.
     */
    fun drawNixie(
        canvas: Canvas,
        text: String,
        cfg: WidgetConfig,
        width: Float,
        height: Float,
        typeface: Typeface,
        density: Float
    ) {
        val cells = SplitFlap.layout(text, width, gap = 2f * density)
        if (cells.isEmpty()) return

        val tubeTop = height * 0.06f
        val tubeHeight = height * 0.88f
        val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
        }

        for (cell in cells) {
            val rect = RectF(cell.left, tubeTop, cell.right, tubeTop + tubeHeight)

            if (!cell.isSeparator) {
                // Glass envelope: a vertical capsule with a soft centre highlight.
                glassPaint.shader = RadialGradient(
                    rect.centerX(), rect.centerY(), rect.width().coerceAtLeast(1f),
                    intArrayOf(
                        Color.argb(70, 255, 170, 90),
                        Color.argb(26, 255, 140, 60),
                        Color.argb(0, 0, 0, 0)
                    ),
                    floatArrayOf(0f, 0.6f, 1f),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(rect, rect.width() / 2f, rect.width() / 2f, glassPaint)
                glassPaint.shader = null

                glassPaint.style = Paint.Style.STROKE
                glassPaint.strokeWidth = (1f * density).coerceAtLeast(1f)
                glassPaint.color = Color.argb(60, 255, 190, 120)
                canvas.drawRoundRect(rect, rect.width() / 2f, rect.width() / 2f, glassPaint)
                glassPaint.style = Paint.Style.FILL
            }

            textPaint.textSize = fitInto(
                textPaint, cell.char.toString(),
                maxWidth = rect.width() * 0.8f,
                maxHeight = tubeHeight * 0.6f
            )
            val metrics = textPaint.fontMetrics
            val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f

            // Glow passes, widest first.
            for (layer in NixieGlow.layers(cfg.glowRadius * density, passes = 3)) {
                textPaint.color = withAlpha(cfg.timeColor.toInt(), layer.alpha)
                textPaint.maskFilter =
                    BlurMaskFilter(layer.blurRadius.coerceAtLeast(0.6f), BlurMaskFilter.Blur.NORMAL)
                canvas.drawText(cell.char.toString(), rect.centerX(), baseline, textPaint)
            }
            textPaint.maskFilter = null
            textPaint.color = cfg.timeColor.toInt()
            canvas.drawText(cell.char.toString(), rect.centerX(), baseline, textPaint)
        }
    }

    // ------------------------------------------------------------------ LCD segment

    /**
     * Liquid-crystal panel. The unlit segments of a real LCD never vanish — they stay as a faint
     * "88:88" ghost, which is drawn first, then the live time on top.
     */
    fun drawLcd(
        canvas: Canvas,
        text: String,
        cfg: WidgetConfig,
        width: Float,
        height: Float,
        typeface: Typeface
    ) {
        // Pixel grid of the panel.
        val cell = LcdGrid.cellSize(height)
        val gridPaint = Paint().apply {
            color = Color.argb(20, 0, 0, 0)
            strokeWidth = 1f
        }
        var x = cell
        while (x < width) {
            canvas.drawLine(x, 0f, x, height, gridPaint); x += cell
        }
        var y = cell
        while (y < height) {
            canvas.drawLine(0f, y, width, y, gridPaint); y += cell
        }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textAlign = Paint.Align.CENTER
            letterSpacing = cfg.letterSpacing
        }
        val ghost = LcdGrid.ghostText(text)
        paint.textSize = fitInto(paint, ghost, width * 0.92f, height * 0.78f)
        val metrics = paint.fontMetrics
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f

        paint.color = withAlpha(cfg.timeColor.toInt(), LcdGrid.GHOST_ALPHA)
        canvas.drawText(ghost, width / 2f, baseline, paint)

        paint.color = cfg.timeColor.toInt()
        canvas.drawText(text, width / 2f, baseline, paint)
    }

    // ------------------------------------------------------------------ LED matrix

    /**
     * A physical LED panel. Every dot of the 5x7 font is drawn as its own square, including the
     * *unlit* ones at low alpha — that dark grid is what makes it read as hardware rather than a
     * dotted typeface.
     */
    fun drawLedMatrix(
        canvas: Canvas,
        text: String,
        cfg: WidgetConfig,
        width: Float,
        height: Float
    ) {
        val panel = DotMatrixFont.render(text, spacing = 1)
        if (panel.isEmpty()) return

        val cols = panel[0].size
        val rows = panel.size
        // One pitch per dot, square, sized so the whole panel fits with a small margin.
        val pitch = minOf(width * 0.94f / cols, height * 0.92f / rows)
        val dot = pitch * 0.82f
        val originX = (width - pitch * cols) / 2f + (pitch - dot) / 2f
        val originY = (height - pitch * rows) / 2f + (pitch - dot) / 2f

        val onPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cfg.timeColor.toInt() }
        val offPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(cfg.timeColor.toInt(), 26)
        }
        if (cfg.glowEnabled) {
            onPaint.setShadowLayer(
                (cfg.glowRadius * 0.4f).coerceAtLeast(1f), 0f, 0f,
                withAlpha(cfg.timeColor.toInt(), 160)
            )
        }
        val corner = dot * 0.2f

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val left = originX + col * pitch
                val top = originY + row * pitch
                canvas.drawRoundRect(
                    RectF(left, top, left + dot, top + dot),
                    corner, corner,
                    if (panel[row][col]) onPaint else offPaint
                )
            }
        }
    }

    // ------------------------------------------------------------------ Retro terminal

    /**
     * Phosphor terminal. Draws a prompt, the time in a monospaced face, a solid block cursor and
     * then lays horizontal scanlines over everything — the lines must come last or the text
     * would sit on top of them and the CRT effect would be lost.
     */
    fun drawTerminal(
        canvas: Canvas,
        text: String,
        cfg: WidgetConfig,
        width: Float,
        height: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.MONOSPACE
            color = cfg.timeColor.toInt()
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.06f
        }
        if (cfg.glowEnabled) {
            paint.setShadowLayer(
                (cfg.glowRadius * 0.5f).coerceAtLeast(1f), 0f, 0f,
                withAlpha(cfg.timeColor.toInt(), 190)
            )
        }

        val prompt = "> "
        val full = prompt + text
        paint.textSize = fitInto(paint, "$full ", width * 0.9f, height * 0.6f)

        val metrics = paint.fontMetrics
        val baseline = height / 2f - (metrics.ascent + metrics.descent) / 2f
        val startX = (width - paint.measureText(full)) / 2f - paint.measureText(" ") / 2f

        canvas.drawText(full, startX.coerceAtLeast(width * 0.04f), baseline, paint)

        // Block cursor, always drawn solid: a widget redraws once a minute, so a "blinking"
        // cursor would just look randomly present or absent.
        val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = withAlpha(cfg.timeColor.toInt(), 170)
        }
        val cursorLeft = startX.coerceAtLeast(width * 0.04f) + paint.measureText(full) +
            paint.measureText(" ") * 0.3f
        val cursorWidth = paint.measureText("0") * 0.8f
        if (cursorLeft + cursorWidth < width) {
            canvas.drawRect(
                cursorLeft,
                baseline + metrics.ascent * 0.8f,
                cursorLeft + cursorWidth,
                baseline + metrics.descent * 0.4f,
                cursorPaint
            )
        }

        val spacing = Scanlines.spacingFor(height, targetLines = 44)
        val linePaint = Paint().apply {
            color = Color.argb(46, 0, 0, 0)
            strokeWidth = (spacing * 0.42f).coerceAtLeast(1f)
        }
        for (y in Scanlines.rows(height, spacing)) {
            canvas.drawLine(0f, y, width, y, linePaint)
        }

        // Vignette: CRTs are never evenly lit at the corners.
        val vignette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                width / 2f, height / 2f, maxOf(width, height) * 0.7f,
                intArrayOf(Color.TRANSPARENT, Color.argb(90, 0, 0, 0)),
                floatArrayOf(0.55f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, width, height, vignette)
    }

    // ------------------------------------------------------------------ helpers

    /** Largest text size at which [text] fits the given box. */
    private fun fitInto(paint: Paint, text: String, maxWidth: Float, maxHeight: Float): Float {
        var size = maxHeight.coerceAtLeast(6f)
        paint.textSize = size
        while (size > 6f &&
            (paint.measureText(text) > maxWidth ||
                paint.fontMetrics.run { descent - ascent } > maxHeight)
        ) {
            size -= 1f
            paint.textSize = size
        }
        return size
    }

    private fun withAlpha(color: Int, alpha: Int) =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun lighten(color: Int, factor: Float) = Color.argb(
        Color.alpha(color),
        (Color.red(color) + (255 - Color.red(color)) * factor).toInt().coerceIn(0, 255),
        (Color.green(color) + (255 - Color.green(color)) * factor).toInt().coerceIn(0, 255),
        (Color.blue(color) + (255 - Color.blue(color)) * factor).toInt().coerceIn(0, 255)
    )

    private fun darken(color: Int, factor: Float) = Color.argb(
        Color.alpha(color),
        (Color.red(color) * (1 - factor)).toInt().coerceIn(0, 255),
        (Color.green(color) * (1 - factor)).toInt().coerceIn(0, 255),
        (Color.blue(color) * (1 - factor)).toInt().coerceIn(0, 255)
    )
}
