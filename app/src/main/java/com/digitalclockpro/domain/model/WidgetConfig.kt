package com.digitalclockpro.domain.model

import kotlinx.serialization.Serializable

/**
 * Everything the Widget Customizer Studio can tweak for a single placed widget
 * (keyed by appWidgetId). Serialized into DataStore as JSON.
 */
@Serializable
data class WidgetConfig(
    val appWidgetId: Int = 0,
    val preset: ClockStyle = ClockStyle.SEVEN_SEGMENT,
    val layout: WidgetLayout = WidgetLayout.SIZE_4x2,

    // ---- Analog ----
    /** Digital readout or analog dial. Chosen on the first tab of the Studio. */
    val clockKind: ClockKind = ClockKind.DIGITAL,
    val analogFace: AnalogFace = AnalogFace.SWISS_MINIMAL,
    val handMotion: HandMotion = HandMotion.QUARTZ,
    val showSecondHand: Boolean = true,
    val showAnalogNumerals: Boolean = true,
    /** Overrides [AnalogFace] colors when the user edits them in the Colors tab. */
    val analogColorsOverridden: Boolean = false,

    // ---- Typography ----
    val fontKey: String = "dseg7_classic",
    /** content:// or file:// Uri of a user-imported .ttf/.otf, null = bundled font. */
    val customFontUri: String? = null,
    val timeTextSizeSp: Float = 58f,
    val dateTextSizeSp: Float = 14f,
    val letterSpacing: Float = 0.02f,

    // ---- Colors & effects ----
    val timeColor: Long = 0xFFFF3B30,
    val dateColor: Long = 0xFFBDBDBD,
    val accentColor: Long = 0xFF00E5FF,
    val gradientEnabled: Boolean = false,
    val gradientEndColor: Long = 0xFF7C4DFF,
    val glowEnabled: Boolean = true,
    val glowRadius: Float = 12f,
    val shadowEnabled: Boolean = false,
    val shadowDx: Float = 2f,
    val shadowDy: Float = 2f,
    val shadowRadius: Float = 4f,
    val shadowColor: Long = 0xA6000000,
    val useDynamicColor: Boolean = false,

    // ---- Background ----
    val backgroundColor: Long = 0xFF000000,
    val backgroundAlpha: Int = 160,          // 0..255
    val cornerRadiusDp: Float = 24f,
    val frostedGlass: Boolean = false,
    val borderEnabled: Boolean = false,
    val borderColor: Long = 0xFF00E5FF,
    val borderWidthDp: Float = 1.5f,

    // ---- Content ----
    val use24Hour: Boolean = false,
    val showSeconds: Boolean = false,
    val showAmPm: Boolean = true,
    val showDate: Boolean = true,
    val datePattern: String = "EEE, MMM d",
    val showBattery: Boolean = true,
    val batteryAsBar: Boolean = true,
    val showNextAlarm: Boolean = true,
    val showWeather: Boolean = false,
    val weatherUnitCelsius: Boolean = true,

    // ---- World clock widget ----
    val cityIds: List<Long> = emptyList(),

    // ---- Tap actions ----
    val tapHours: TapAction = TapAction.OPEN_ALARMS,
    val tapMinutes: TapAction = TapAction.OPEN_APP,
    val tapDate: TapAction = TapAction.OPEN_CALENDAR,
    val tapWeather: TapAction = TapAction.OPEN_WEATHER
)

/**
 * Visual presets for the digital clock.
 *
 * No display name here on purpose: the label lives in `strings.xml` and is resolved in the
 * presentation layer, so the catalogue is translatable. The enum is serialized **by name**, so
 * entries may be appended freely but must never be renamed or reordered away — an unknown name
 * would fail to deserialize a widget the user already placed.
 */
@Serializable
enum class ClockStyle {
    SEVEN_SEGMENT,
    CYBERPUNK_NEON,
    DOT_MATRIX,
    MINIMAL_MONO,
    RETRO_FLIP,
    AMOLED_BLACK,
    GLASSMORPHISM,

    // ---- Phase 2 ----
    /** Airport departure board: every character sits on a hinged flap card. */
    SPLIT_FLAP,
    /** Cold-war cathode tubes with an orange glow inside a glass envelope. */
    NIXIE_TUBE,
    /** Liquid-crystal panel: the unlit "88:88" segments stay faintly visible behind the time. */
    LCD_SEGMENT,
    /** A real LED panel – each pixel is drawn as its own square dot. */
    LED_MATRIX,
    /** Green/amber phosphor terminal with CRT scanlines and a blinking prompt. */
    RETRO_TERMINAL
}

@Serializable
enum class WidgetLayout(val cols: Int, val rows: Int, val label: String) {
    SIZE_2x1(2, 1, "2x1"),
    SIZE_4x1(4, 1, "4x1"),
    SIZE_4x2(4, 2, "4x2"),
    SIZE_4x3(4, 3, "4x3"),
    SIZE_5x1(5, 1, "5x1"),
    SIZE_5x2(5, 2, "5x2")
}

@Serializable
enum class TapAction {
    NONE, OPEN_APP, OPEN_ALARMS, OPEN_WORLD_CLOCK, OPEN_CALENDAR, OPEN_WEATHER, OPEN_WIDGET_SETTINGS
}
