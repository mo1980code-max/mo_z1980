package com.digitalclockpro.clockengine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class LocaleTextTest {

    private val en = Locale.ENGLISH
    private val ar = Locale("ar")
    private val arJo = Locale("ar", "JO")
    private val he = Locale("he")
    private val fa = Locale("fa")

    // ---------------------------------------------------------------- isRtl

    @Test
    fun `arabic is rtl`() = assertTrue(LocaleText.isRtl(ar))

    @Test
    fun `arabic with a region is rtl`() = assertTrue(LocaleText.isRtl(arJo))

    @Test
    fun `hebrew is rtl under both the modern and legacy codes`() {
        assertTrue(LocaleText.isRtl(he))
        // The JDK normalises "he" to the legacy "iw" in Locale.getLanguage() on many versions.
        assertTrue(LocaleText.isRtl(Locale("iw")))
    }

    @Test
    fun `persian urdu and pashto are rtl`() {
        assertTrue(LocaleText.isRtl(fa))
        assertTrue(LocaleText.isRtl(Locale("ur")))
        assertTrue(LocaleText.isRtl(Locale("ps")))
    }

    @Test
    fun `english french and japanese are not rtl`() {
        assertFalse(LocaleText.isRtl(en))
        assertFalse(LocaleText.isRtl(Locale.FRENCH))
        assertFalse(LocaleText.isRtl(Locale.JAPANESE))
    }

    @Test
    fun `language code case does not matter`() {
        assertTrue(LocaleText.isRtl(Locale("AR")))
    }

    // ---------------------------------------------------------------- digits

    @Test
    fun `english digits are returned untouched`() {
        assertEquals("09:41", LocaleText.localizeDigits("09:41", en))
    }

    @Test
    fun `an english locale returns the identical instance path`() {
        val input = "12 / 2026"
        assertEquals(input, LocaleText.localizeDigits(input, en))
    }

    @Test
    fun `arabic digits are mapped to arabic-indic`() {
        // Only assert the mapping is consistent with the JDK's own symbols for this locale,
        // not a hardcoded glyph: the JDK's choice differs between ar and ar-JO on some builds.
        val zero = java.text.DecimalFormatSymbols.getInstance(ar).zeroDigit
        val expected = buildString { for (c in "0941") append(zero + (c - '0')) }
        assertEquals(expected, LocaleText.localizeDigits("0941", ar))
    }

    @Test
    fun `separators and symbols survive digit mapping`() {
        val out = LocaleText.localizeDigits("09:41 • 80% • 21°C", ar)
        assertTrue(out.contains(':'))
        assertTrue(out.contains('•'))
        assertTrue(out.contains('%'))
        assertTrue(out.contains('°'))
        assertTrue(out.contains('C'))
        // No ASCII digit may survive when the locale has its own zero.
        val zero = java.text.DecimalFormatSymbols.getInstance(ar).zeroDigit
        if (zero != '0') assertFalse(out.any { it in '0'..'9' })
    }

    @Test
    fun `an empty string maps to an empty string`() {
        assertEquals("", LocaleText.localizeDigits("", ar))
    }

    @Test
    fun `a string with no digits is unchanged`() {
        assertEquals("مساء", LocaleText.localizeDigits("مساء", ar))
    }

    @Test
    fun `the int overload agrees with the string overload`() {
        assertEquals(LocaleText.localizeDigits("12", ar), LocaleText.localizeDigits(12, ar))
    }

    @Test
    fun `every clock face numeral round-trips`() {
        val zero = java.text.DecimalFormatSymbols.getInstance(ar).zeroDigit
        for (hour in 1..12) {
            val out = LocaleText.localizeDigits(hour, ar)
            val expected = buildString { for (c in hour.toString()) append(zero + (c - '0')) }
            assertEquals(expected, out)
        }
    }

    @Test
    fun `digit mapping does not change length`() {
        assertEquals(5, LocaleText.localizeDigits("09:41", ar).length)
    }

    // ---------------------------------------------------------------- join order

    @Test
    fun `ltr keeps the given order`() {
        assertEquals(
            "a | b | c",
            LocaleText.joinForDisplay(listOf("a", "b", "c"), " | ", rtl = false)
        )
    }

    @Test
    fun `rtl reverses so the first fragment is read first from the right`() {
        assertEquals(
            "c | b | a",
            LocaleText.joinForDisplay(listOf("a", "b", "c"), " | ", rtl = true)
        )
    }

    @Test
    fun `a single fragment is unaffected by direction`() {
        assertEquals("only", LocaleText.joinForDisplay(listOf("only"), " | ", rtl = true))
        assertEquals("only", LocaleText.joinForDisplay(listOf("only"), " | ", rtl = false))
    }

    @Test
    fun `an empty list joins to an empty string in both directions`() {
        assertEquals("", LocaleText.joinForDisplay(emptyList(), " | ", rtl = true))
        assertEquals("", LocaleText.joinForDisplay(emptyList(), " | ", rtl = false))
    }

    @Test
    fun `joining does not mutate the caller's list`() {
        val parts = listOf("a", "b", "c")
        LocaleText.joinForDisplay(parts, " | ", rtl = true)
        assertEquals(listOf("a", "b", "c"), parts)
    }

    // ---------------------------------------------------------------- isolation

    @Test
    fun `isolate wraps with FSI and PDI`() {
        val out = LocaleText.isolate("06:30")
        assertEquals(LocaleText.FSI, out.first())
        assertEquals(LocaleText.PDI, out.last())
        assertTrue(out.contains("06:30"))
    }

    @Test
    fun `isolating an empty string adds nothing`() {
        assertEquals("", LocaleText.isolate(""))
    }

    @Test
    fun `isolation adds exactly two characters`() {
        assertEquals(7, LocaleText.isolate("06:30").length)
    }
}
