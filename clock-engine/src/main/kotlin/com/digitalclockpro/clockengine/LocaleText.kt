package com.digitalclockpro.clockengine

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Locale and bidirectional-text helpers for everything drawn onto a `Canvas`.
 *
 * Canvas drawing gets none of the automatic locale handling that `TextView` and Compose `Text`
 * provide. Nothing mirrors, nothing re-orders, and digits are whatever the caller passed in.
 * These helpers are the pure, testable half of that problem; the Android half (choosing an
 * anchor x, picking `Paint.Align`) stays in the renderers.
 */
object LocaleText {

    /**
     * Languages written right-to-left, by ISO 639-1/639-2 code.
     *
     * Hardcoded rather than derived. `Character.getDirectionality` answers a question about one
     * character, not about a language, and ICU's `ULocale` is not on the JVM test classpath.
     * This list is what Android's own `TextUtils.getLayoutDirectionFromLocale` effectively
     * encodes, and it is stable — new RTL scripts are not invented often.
     *
     * `iw`, `in` and `ji` are the obsolete codes for Hebrew, Indonesian and Yiddish that the
     * JDK still returns from `Locale.getLanguage()` for backwards compatibility, so both
     * spellings have to be here or Hebrew silently falls through as LTR.
     */
    private val RTL_LANGUAGES = setOf(
        "ar",  // Arabic
        "dv",  // Divehi
        "fa",  // Persian
        "he", "iw",  // Hebrew (modern, legacy)
        "ku",  // Kurdish (Sorani)
        "ps",  // Pashto
        "sd",  // Sindhi
        "ug",  // Uyghur
        "ur",  // Urdu
        "yi", "ji"   // Yiddish (modern, legacy)
    )

    fun isRtl(locale: Locale): Boolean = locale.language.lowercase(Locale.ROOT) in RTL_LANGUAGES

    /**
     * Rewrites ASCII digits into the locale's own digit shapes.
     *
     * Arabic locales render times as `٠٩:٤١` through `DateTimeFormatter`, because java.time
     * consults [DecimalFormatSymbols]. Anything hand-built with `Int.toString()` — clock-face
     * numerals, a battery percentage, a lap counter — skips that and comes out as `09:41`,
     * leaving two different digit systems side by side in one screen.
     *
     * Only `0`–`9` are touched. Separators, `%`, `°` and letters are left exactly as they were.
     */
    fun localizeDigits(text: String, locale: Locale): String {
        val zero = DecimalFormatSymbols.getInstance(locale).zeroDigit
        if (zero == '0') return text
        val offset = zero - '0'
        val out = StringBuilder(text.length)
        for (c in text) out.append(if (c in '0'..'9') c + offset else c)
        return out.toString()
    }

    fun localizeDigits(value: Int, locale: Locale): String = localizeDigits(value.toString(), locale)

    /**
     * Joins display fragments in the order they should appear, left to right, on screen.
     *
     * A widget's secondary line is built as `[date, battery, alarm, weather]` in reading order.
     * Under a right-to-left locale, reading order starts at the right, so the fragment that must
     * be drawn leftmost is the last one. `Canvas.drawText` applies the Unicode bidi algorithm per
     * run, but it cannot know that these fragments are a list — and when the first fragment
     * begins with a digit (a date like `04/10`), the paragraph direction resolves to LTR and the
     * whole line comes out in the wrong order.
     *
     * Reversing here makes the visual order explicit instead of depending on which character
     * happens to come first.
     */
    fun joinForDisplay(parts: List<String>, separator: String, rtl: Boolean): String =
        if (rtl) parts.asReversed().joinToString(separator) else parts.joinToString(separator)

    /**
     * Wraps a fragment so an embedded opposite-direction run cannot escape and reorder its
     * neighbours.
     *
     * `U+2068 FIRST STRONG ISOLATE` and `U+2069 POP DIRECTIONAL ISOLATE` are the modern
     * replacement for the deprecated embedding marks: the isolate decides its own direction from
     * its first strong character and is treated as a single neutral object by whatever surrounds
     * it. Without this, `⏰ 06:30` sitting next to Arabic text can drag the clock emoji to the
     * wrong end of the line.
     */
    fun isolate(text: String): String =
        if (text.isEmpty()) text else "$FSI$text$PDI"

    const val FSI = '\u2068'
    const val PDI = '\u2069'
}
