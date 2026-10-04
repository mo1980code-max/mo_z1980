package com.digitalclockpro.domain.model

import kotlinx.serialization.Serializable

/** Whether a widget / screen renders a digital readout or an analog dial. */
@Serializable
enum class ClockKind { DIGITAL, ANALOG }

/**
 * The six bundled analog dials.
 *
 * Each face is a complete visual recipe — the renderer reads nothing but these values, so adding
 * a seventh face never means touching drawing code. Colors are ARGB longs to match the rest of
 * [WidgetConfig]; they are only defaults and every one of them stays overridable in the Studio.
 */
@Serializable
enum class AnalogFace(
    val dialColor: Long,
    val rimColor: Long,
    val tickColor: Long,
    val numeralColor: Long,
    val hourHandColor: Long,
    val minuteHandColor: Long,
    val secondHandColor: Long,
    val numerals: NumeralStyle,
    /** Only the 12 major ticks, or all 60. */
    val minuteTicks: Boolean,
    val glowRadius: Float = 0f,
    /** Draws a brushed-metal / gradient sheen across the dial. */
    val metallicSheen: Boolean = false,
    /** Small digital readout inside the dial (Digital Hybrid). */
    val digitalInset: Boolean = false,
    /** Decorative gears behind the hands (Steampunk). */
    val gearDecoration: Boolean = false,
    val handStyle: HandStyle = HandStyle.BATON
) {
    /** Clean white dial, no numerals, thin black batons. */
    SWISS_MINIMAL(
        dialColor = 0xFFFAFAFA,
        rimColor = 0xFFE0E0E0,
        tickColor = 0xFF212121,
        numeralColor = 0xFF212121,
        hourHandColor = 0xFF212121,
        minuteHandColor = 0xFF212121,
        secondHandColor = 0xFFD32F2F,
        numerals = NumeralStyle.NONE,
        minuteTicks = true,
        handStyle = HandStyle.BATON
    ),

    /** Cream dial with roman numerals and leaf-shaped hands. */
    CLASSIC_ROMAN(
        dialColor = 0xFFFDF6E3,
        rimColor = 0xFF8D6E63,
        tickColor = 0xFF5D4037,
        numeralColor = 0xFF3E2723,
        hourHandColor = 0xFF3E2723,
        minuteHandColor = 0xFF3E2723,
        secondHandColor = 0xFF8D6E63,
        numerals = NumeralStyle.ROMAN,
        minuteTicks = false,
        handStyle = HandStyle.LEAF
    ),

    /** Black dial, gold rim, gold arabic numerals, metallic sheen. */
    LUXURY_GOLD(
        dialColor = 0xFF14110E,
        rimColor = 0xFFD4AF37,
        tickColor = 0xFFD4AF37,
        numeralColor = 0xFFF5D98B,
        hourHandColor = 0xFFD4AF37,
        minuteHandColor = 0xFFF5D98B,
        secondHandColor = 0xFFB76E79,
        numerals = NumeralStyle.ARABIC,
        minuteTicks = true,
        metallicSheen = true,
        handStyle = HandStyle.LEAF
    ),

    /** Pure black with cyan/magenta neon glow — the AMOLED-friendly face. */
    NEON_ANALOG(
        dialColor = 0xFF000000,
        rimColor = 0xFF00E5FF,
        tickColor = 0xFF00E5FF,
        numeralColor = 0xFF00E5FF,
        hourHandColor = 0xFF00E5FF,
        minuteHandColor = 0xFF00E5FF,
        secondHandColor = 0xFFFF2D95,
        numerals = NumeralStyle.ARABIC,
        minuteTicks = false,
        glowRadius = 14f,
        handStyle = HandStyle.BATON
    ),

    /** Aged brass with exposed gears and skeleton hands. */
    STEAMPUNK(
        dialColor = 0xFF3B2F2F,
        rimColor = 0xFFB08D57,
        tickColor = 0xFFD7C49E,
        numeralColor = 0xFFD7C49E,
        hourHandColor = 0xFFE8D5A3,
        minuteHandColor = 0xFFE8D5A3,
        secondHandColor = 0xFFC1440E,
        numerals = NumeralStyle.ROMAN,
        minuteTicks = true,
        metallicSheen = true,
        gearDecoration = true,
        handStyle = HandStyle.SKELETON
    ),

    /** Analog hands with a small digital time + date readout in the lower half. */
    DIGITAL_HYBRID(
        dialColor = 0xFF101418,
        rimColor = 0xFF37474F,
        tickColor = 0xFF90A4AE,
        numeralColor = 0xFFECEFF1,
        hourHandColor = 0xFFECEFF1,
        minuteHandColor = 0xFFECEFF1,
        secondHandColor = 0xFF00E676,
        numerals = NumeralStyle.ARABIC_QUARTERS,
        minuteTicks = true,
        digitalInset = true,
        handStyle = HandStyle.BATON
    );

    @Serializable
    enum class NumeralStyle {
        NONE,
        /** 1..12 */
        ARABIC,
        /** 12, 3, 6, 9 only */
        ARABIC_QUARTERS,
        /** XII, I, II … */
        ROMAN
    }

    @Serializable
    enum class HandStyle {
        /** Straight rectangle with rounded caps. */
        BATON,
        /** Tapered diamond. */
        LEAF,
        /** Open outline. */
        SKELETON
    }
}

/**
 * Serializable mirror of `ClockEngine.HandMotion`.
 *
 * The engine is a pure module with no serialization dependency, so the persisted enum lives here
 * and [toEngine] bridges the two. Keeping them separate means the engine never has to know that
 * anything is stored on disk.
 */
@Serializable
enum class HandMotion {
    /** Second hand jumps once per second. */
    QUARTZ,

    /** Continuous sweep; on-screen only — widgets always fall back to quartz. */
    SMOOTH;

    fun toEngine(): com.digitalclockpro.clockengine.ClockEngine.HandMotion = when (this) {
        QUARTZ -> com.digitalclockpro.clockengine.ClockEngine.HandMotion.QUARTZ
        SMOOTH -> com.digitalclockpro.clockengine.ClockEngine.HandMotion.SMOOTH
    }
}
