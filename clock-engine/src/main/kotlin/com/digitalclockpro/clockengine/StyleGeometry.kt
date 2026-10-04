package com.digitalclockpro.clockengine

/**
 * Layout maths shared by the decorative digital styles.
 *
 * The Android renderers only turn these numbers into Canvas calls; all the arithmetic that could
 * silently be wrong lives here, where it is covered by unit tests.
 */

/** Airport departure-board style: one hinged card per character. */
object SplitFlap {

    /** The hinge sits exactly halfway down a card — that is what makes the fold read as a flap. */
    const val HINGE_FRACTION = 0.5f

    data class Cell(
        val char: Char,
        val index: Int,
        /** Left edge of the card, in pixels, relative to the start of the row. */
        val left: Float,
        val width: Float,
        /** Separators are drawn without a card so ':' does not look like a flap. */
        val isSeparator: Boolean
    ) {
        val right: Float get() = left + width
        val hingeY: Float get() = HINGE_FRACTION
    }

    /**
     * Distributes [text] across [totalWidth].
     *
     * Separators get a narrower card so "12:34" does not waste a full flap on the colon; the
     * remaining width is shared equally by the digits, which keeps the board visually even.
     */
    fun layout(
        text: String,
        totalWidth: Float,
        gap: Float = 0f,
        separatorWidthRatio: Float = 0.45f
    ): List<Cell> {
        if (text.isEmpty() || totalWidth <= 0f) return emptyList()

        val separators = text.count { isSeparator(it) }
        val digits = text.length - separators
        val gaps = gap.coerceAtLeast(0f) * (text.length - 1)
        val usable = (totalWidth - gaps).coerceAtLeast(0f)

        // digits * w + separators * (w * ratio) = usable
        val ratio = separatorWidthRatio.coerceIn(0.1f, 1f)
        val denominator = digits + separators * ratio
        val digitWidth = if (denominator <= 0f) 0f else usable / denominator

        var x = 0f
        return text.mapIndexed { index, char ->
            val separator = isSeparator(char)
            val width = if (separator) digitWidth * ratio else digitWidth
            Cell(char, index, x, width, separator).also { x += width + gap }
        }
    }

    fun isSeparator(char: Char): Boolean = char == ':' || char == '.' || char == ' '
}

/** CRT phosphor effect for the Retro Terminal style. */
object Scanlines {

    /**
     * Y positions of the dark lines across a panel of [heightPx].
     *
     * The first line is offset by half a period so the top edge never starts on a dark row,
     * which otherwise looks like a rendering bug rather than a CRT.
     */
    fun rows(heightPx: Float, spacingPx: Float): List<Float> {
        if (heightPx <= 0f || spacingPx <= 0f) return emptyList()
        val result = mutableListOf<Float>()
        var y = spacingPx / 2f
        while (y < heightPx) {
            result += y
            y += spacingPx
        }
        return result
    }

    /** Scanline spacing that stays visible but never moirés, whatever the widget height. */
    fun spacingFor(heightPx: Float, targetLines: Int = 48): Float =
        if (heightPx <= 0f) 0f else (heightPx / targetLines.coerceAtLeast(1)).coerceAtLeast(2f)
}

/** Concentric glow passes that fake the warm cathode haze inside a Nixie tube. */
object NixieGlow {

    data class Layer(val blurRadius: Float, val alpha: Int)

    /**
     * Outer passes are wide and faint, inner passes tight and bright. Drawing them back to front
     * builds up the characteristic orange halo without needing a real blur shader.
     */
    fun layers(baseRadius: Float, passes: Int = 3): List<Layer> {
        if (baseRadius <= 0f || passes <= 0) return emptyList()
        return (passes downTo 1).map { step ->
            val fraction = step.toFloat() / passes
            Layer(
                blurRadius = baseRadius * fraction,
                alpha = (255 * (1f - fraction) * 0.5f + 40).toInt().coerceIn(0, 255)
            )
        }
    }
}

/** Background grid of an LCD panel: the faint "off" segments you see on a real display. */
object LcdGrid {

    /** Alpha of the unlit ghost text behind the real digits. */
    const val GHOST_ALPHA = 28

    /** The all-segments-on string used to draw the ghost, e.g. "88:88" for "12:34". */
    fun ghostText(text: String): String = buildString {
        for (char in text) append(if (char.isDigit()) '8' else char)
    }

    /** Pixel pitch of the background grid, scaled so it stays ~1 cell per 6 dp. */
    fun cellSize(heightPx: Float, cells: Int = 18): Float =
        if (heightPx <= 0f) 0f else (heightPx / cells.coerceAtLeast(1)).coerceAtLeast(3f)
}
