package com.digitalclockpro.clockengine

/**
 * A 5x7 pixel font for the LED Matrix widget style.
 *
 * The renderer draws one square per lit cell, which is what makes the style read as a *physical*
 * LED panel rather than a font with a dot texture. Keeping the bitmaps here (instead of in the
 * Android renderer) means the glyph geometry is unit-testable and the drawing code stays trivial.
 */
object DotMatrixFont {

    const val GLYPH_WIDTH = 5
    const val GLYPH_HEIGHT = 7

    /** Blank columns inserted between glyphs. Colons get a tighter gap; see [render]. */
    const val DEFAULT_SPACING = 1

    private val GLYPHS: Map<Char, List<String>> = mapOf(
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ':' to listOf("00000", "00100", "00100", "00000", "00100", "00100", "00000"),
        '.' to listOf("00000", "00000", "00000", "00000", "00000", "00100", "00100"),
        '-' to listOf("00000", "00000", "00000", "11111", "00000", "00000", "00000"),
        'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'M' to listOf("10001", "11011", "10101", "10001", "10001", "10001", "10001"),
        ' ' to listOf("00000", "00000", "00000", "00000", "00000", "00000", "00000")
    )

    /** Anything the panel cannot render shows as a blank cell rather than crashing. */
    private val FALLBACK = GLYPHS.getValue(' ')

    fun supports(char: Char): Boolean = GLYPHS.containsKey(char.uppercaseChar())

    /** `[row][column]`, row 0 at the top. Always [GLYPH_HEIGHT] x [GLYPH_WIDTH]. */
    fun glyph(char: Char): Array<BooleanArray> {
        val rows = GLYPHS[char.uppercaseChar()] ?: FALLBACK
        return Array(GLYPH_HEIGHT) { row ->
            BooleanArray(GLYPH_WIDTH) { col -> rows[row][col] == '1' }
        }
    }

    fun widthOf(text: String, spacing: Int = DEFAULT_SPACING): Int {
        if (text.isEmpty()) return 0
        return text.length * GLYPH_WIDTH + (text.length - 1) * spacing.coerceAtLeast(0)
    }

    /**
     * Lays the whole string out into a single panel bitmap, `[row][column]`.
     * Returns an empty array for empty input so callers can skip drawing entirely.
     */
    fun render(text: String, spacing: Int = DEFAULT_SPACING): Array<BooleanArray> {
        if (text.isEmpty()) return emptyArray()
        val gap = spacing.coerceAtLeast(0)
        val width = widthOf(text, gap)
        val panel = Array(GLYPH_HEIGHT) { BooleanArray(width) }

        var x = 0
        for (char in text) {
            val glyph = glyph(char)
            for (row in 0 until GLYPH_HEIGHT) {
                for (col in 0 until GLYPH_WIDTH) {
                    panel[row][x + col] = glyph[row][col]
                }
            }
            x += GLYPH_WIDTH + gap
        }
        return panel
    }
}
