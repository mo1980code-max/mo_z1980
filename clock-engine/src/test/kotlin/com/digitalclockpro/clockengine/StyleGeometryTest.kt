package com.digitalclockpro.clockengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DotMatrixFontTest {

    @Test
    fun `every glyph is five by seven`() {
        "0123456789:.-APM ".forEach { char ->
            val glyph = DotMatrixFont.glyph(char)
            assertEquals("height of '$char'", DotMatrixFont.GLYPH_HEIGHT, glyph.size)
            glyph.forEach { row ->
                assertEquals("width of '$char'", DotMatrixFont.GLYPH_WIDTH, row.size)
            }
        }
    }

    @Test
    fun `digits are declared as supported and random symbols are not`() {
        assertTrue(DotMatrixFont.supports('7'))
        assertTrue(DotMatrixFont.supports(':'))
        assertTrue(DotMatrixFont.supports('a'))   // case insensitive
        assertFalse(DotMatrixFont.supports('§'))
    }

    @Test
    fun `an unsupported character renders blank instead of crashing`() {
        val glyph = DotMatrixFont.glyph('§')
        assertTrue(glyph.all { row -> row.none { it } })
    }

    @Test
    fun `the one glyph has fewer lit pixels than the eight glyph`() {
        val one = DotMatrixFont.glyph('1').sumOf { row -> row.count { it } }
        val eight = DotMatrixFont.glyph('8').sumOf { row -> row.count { it } }
        assertTrue("one=$one eight=$eight", one < eight)
    }

    @Test
    fun `a colon lights only the two middle dots`() {
        val glyph = DotMatrixFont.glyph(':')
        assertEquals(4, glyph.sumOf { row -> row.count { it } })
        assertTrue(glyph[0].none { it })
        assertTrue(glyph[6].none { it })
    }

    @Test
    fun `width accounts for inter-glyph spacing`() {
        assertEquals(0, DotMatrixFont.widthOf(""))
        assertEquals(5, DotMatrixFont.widthOf("1", spacing = 1))
        // 5 chars * 5 px + 4 gaps * 1 px
        assertEquals(29, DotMatrixFont.widthOf("12:34", spacing = 1))
        assertEquals(37, DotMatrixFont.widthOf("12:34", spacing = 3))
    }

    @Test
    fun `rendering a string produces a panel of the declared width`() {
        val panel = DotMatrixFont.render("12:34")
        assertEquals(DotMatrixFont.GLYPH_HEIGHT, panel.size)
        assertEquals(DotMatrixFont.widthOf("12:34"), panel[0].size)
    }

    @Test
    fun `rendering places the second glyph after the gap`() {
        val panel = DotMatrixFont.render("18", spacing = 1)
        val eight = DotMatrixFont.glyph('8')
        for (row in 0 until DotMatrixFont.GLYPH_HEIGHT) {
            for (col in 0 until DotMatrixFont.GLYPH_WIDTH) {
                assertEquals(eight[row][col], panel[row][6 + col])
            }
        }
        // The gap column itself must stay dark.
        assertTrue(panel.all { !it[5] })
    }

    @Test
    fun `an empty string renders nothing`() {
        assertEquals(0, DotMatrixFont.render("").size)
    }
}

class SplitFlapTest {

    @Test
    fun `layout returns one cell per character`() {
        assertEquals(5, SplitFlap.layout("12:34", totalWidth = 500f).size)
    }

    @Test
    fun `cells fill the available width exactly`() {
        val cells = SplitFlap.layout("12:34", totalWidth = 500f)
        assertEquals(0f, cells.first().left, 0.001f)
        assertEquals(500f, cells.last().right, 0.01f)
    }

    @Test
    fun `gaps are subtracted from the usable width`() {
        val gap = 4f
        val cells = SplitFlap.layout("12:34", totalWidth = 500f, gap = gap)
        assertEquals(500f, cells.last().right, 0.01f)
        assertEquals(cells[0].right + gap, cells[1].left, 0.001f)
    }

    @Test
    fun `separators get a narrower card than digits`() {
        val cells = SplitFlap.layout("12:34", totalWidth = 500f)
        val colon = cells.first { it.isSeparator }
        val digit = cells.first { !it.isSeparator }
        assertTrue(colon.width < digit.width)
        assertEquals(digit.width * 0.45f, colon.width, 0.01f)
    }

    @Test
    fun `all digits share the same width`() {
        val widths = SplitFlap.layout("12:34", totalWidth = 500f)
            .filter { !it.isSeparator }
            .map { it.width }
        widths.forEach { assertEquals(widths.first(), it, 0.001f) }
    }

    @Test
    fun `cells never overlap`() {
        val cells = SplitFlap.layout("12:34:56", totalWidth = 800f, gap = 2f)
        cells.zipWithNext { a, b -> assertTrue(a.right <= b.left + 0.001f) }
    }

    @Test
    fun `degenerate input returns no cells`() {
        assertTrue(SplitFlap.layout("", 500f).isEmpty())
        assertTrue(SplitFlap.layout("12:34", totalWidth = 0f).isEmpty())
    }

    @Test
    fun `the hinge is always at mid height`() {
        SplitFlap.layout("12:34", 500f).forEach {
            assertEquals(0.5f, it.hingeY, 0.0001f)
        }
    }
}

class ScanlinesTest {

    @Test
    fun `lines are evenly spaced and start half a period in`() {
        val rows = Scanlines.rows(heightPx = 100f, spacingPx = 10f)
        assertEquals(10, rows.size)
        assertEquals(5f, rows.first(), 0.001f)
        assertEquals(95f, rows.last(), 0.001f)
    }

    @Test
    fun `no line ever lands on the top or bottom edge`() {
        val height = 100f
        Scanlines.rows(height, 10f).forEach {
            assertTrue(it > 0f && it < height)
        }
    }

    @Test
    fun `degenerate sizes produce no lines`() {
        assertTrue(Scanlines.rows(0f, 10f).isEmpty())
        assertTrue(Scanlines.rows(100f, 0f).isEmpty())
        assertTrue(Scanlines.rows(100f, -4f).isEmpty())
    }

    @Test
    fun `spacing never collapses below two pixels`() {
        assertEquals(2f, Scanlines.spacingFor(heightPx = 20f, targetLines = 48), 0.001f)
        assertEquals(10f, Scanlines.spacingFor(heightPx = 480f, targetLines = 48), 0.001f)
    }
}

class NixieGlowTest {

    @Test
    fun `layers run from wide and faint to tight and bright`() {
        val layers = NixieGlow.layers(baseRadius = 12f, passes = 3)
        assertEquals(3, layers.size)
        assertTrue(layers.first().blurRadius > layers.last().blurRadius)
        assertTrue(layers.first().alpha < layers.last().alpha)
    }

    @Test
    fun `alpha always stays a legal channel value`() {
        NixieGlow.layers(40f, passes = 6).forEach {
            assertTrue(it.alpha in 0..255)
        }
    }

    @Test
    fun `degenerate input produces no layers`() {
        assertTrue(NixieGlow.layers(0f).isEmpty())
        assertTrue(NixieGlow.layers(10f, passes = 0).isEmpty())
    }
}

class LcdGridTest {

    @Test
    fun `the ghost turns every digit into an eight`() {
        assertEquals("88:88", LcdGrid.ghostText("12:34"))
        assertEquals("88:88:88", LcdGrid.ghostText("09:05:41"))
    }

    @Test
    fun `non digits are left untouched by the ghost`() {
        assertEquals("88:88 AM", LcdGrid.ghostText("12:34 AM"))
    }

    @Test
    fun `the ghost is always the same length as the real text`() {
        listOf("1:05", "23:59:59", "", "12:34 PM").forEach {
            assertEquals(it.length, LcdGrid.ghostText(it).length)
        }
    }

    @Test
    fun `grid cells never collapse below three pixels`() {
        assertEquals(3f, LcdGrid.cellSize(heightPx = 10f), 0.001f)
        assertEquals(10f, LcdGrid.cellSize(heightPx = 180f, cells = 18), 0.001f)
        assertEquals(0f, LcdGrid.cellSize(heightPx = 0f), 0.001f)
    }
}
