package com.digitalclockpro.presentation.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.digitalclockpro.domain.model.ClockStyle
import com.digitalclockpro.domain.model.TapAction
import com.digitalclockpro.domain.model.WidgetConfig
import com.digitalclockpro.domain.model.WidgetLayout
import com.digitalclockpro.domain.repository.WidgetConfigRepository
import com.digitalclockpro.widget.WidgetUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.digitalclockpro.domain.model.AnalogFace
import com.digitalclockpro.domain.model.ClockKind

@HiltViewModel
class WidgetStudioViewModel @Inject constructor(
    private val repository: WidgetConfigRepository,
    private val widgetUpdater: WidgetUpdater
) : ViewModel() {

    private val _config = MutableStateFlow(WidgetConfig())
    val config: StateFlow<WidgetConfig> = _config.asStateFlow()

    fun load(appWidgetId: Int) = viewModelScope.launch {
        _config.value = repository.getConfig(appWidgetId).copy(appWidgetId = appWidgetId)
    }

    fun update(transform: (WidgetConfig) -> WidgetConfig) = _config.update(transform)

    /** Switches between a digital readout and an analog dial. */
    fun setClockKind(kind: ClockKind) = _config.update { it.copy(clockKind = kind) }

    /**
     * Applies an analog face. The face carries its own palette, so the manual colour override is
     * cleared — otherwise picking a new dial would appear to do nothing.
     */
    fun setAnalogFace(face: AnalogFace) = _config.update {
        it.copy(clockKind = ClockKind.ANALOG, analogFace = face, analogColorsOverridden = false)
    }

    /** Applies a full visual preset in one tap (Presets tab). */
    fun applyPreset(style: ClockStyle) = _config.update { current ->
        when (style) {
            ClockStyle.SEVEN_SEGMENT -> current.copy(
                preset = style, fontKey = "dseg7_classic", timeColor = 0xFFFF3B30,
                dateColor = 0xFFBDBDBD, backgroundColor = 0xFF000000, backgroundAlpha = 200,
                glowEnabled = true, glowRadius = 10f, gradientEnabled = false, frostedGlass = false
            )
            ClockStyle.CYBERPUNK_NEON -> current.copy(
                preset = style, fontKey = "cyberpunk", timeColor = 0xFF00E5FF,
                dateColor = 0xFFFF2D95, accentColor = 0xFFFF2D95, gradientEnabled = true,
                gradientEndColor = 0xFFFF2D95, glowEnabled = true, glowRadius = 18f,
                backgroundColor = 0xFF12002A, backgroundAlpha = 220, borderEnabled = true
            )
            ClockStyle.DOT_MATRIX -> current.copy(
                preset = style, fontKey = "led_dot_matrix", timeColor = 0xFFFFB300,
                dateColor = 0xFFFFB300, glowEnabled = true, glowRadius = 8f,
                backgroundColor = 0xFF0A0A0A, backgroundAlpha = 230
            )
            ClockStyle.MINIMAL_MONO -> current.copy(
                preset = style, fontKey = "jetbrains_mono", timeColor = 0xFFFFFFFF,
                dateColor = 0xFFBDBDBD, glowEnabled = false, shadowEnabled = false,
                gradientEnabled = false, backgroundAlpha = 0, letterSpacing = 0.0f
            )
            ClockStyle.RETRO_FLIP -> current.copy(
                preset = style, fontKey = "flip_clock", timeColor = 0xFFF5F5F5,
                dateColor = 0xFF9E9E9E, glowEnabled = false, shadowEnabled = true,
                backgroundColor = 0xFF1E1E1E, backgroundAlpha = 255, cornerRadiusDp = 14f
            )
            ClockStyle.AMOLED_BLACK -> current.copy(
                preset = style, fontKey = "inter_tight", timeColor = 0xFFFFFFFF,
                dateColor = 0xFF757575, accentColor = 0xFF00E5FF, glowEnabled = false,
                backgroundColor = 0xFF000000, backgroundAlpha = 255, borderEnabled = true,
                borderColor = 0xFF00E5FF
            )
            ClockStyle.GLASSMORPHISM -> current.copy(
                preset = style, fontKey = "poppins", timeColor = 0xFFFFFFFF,
                dateColor = 0xFFE0E0E0, glowEnabled = false, frostedGlass = true,
                backgroundColor = 0xFFFFFFFF, backgroundAlpha = 60, cornerRadiusDp = 28f,
                borderEnabled = true, borderColor = 0x59FFFFFF
            )

            // ---- Phase 2 ----

            // The card colour IS the background here: StyleRenderers paints each flap with
            // backgroundColor, so the widget's own backdrop is left nearly opaque behind it.
            ClockStyle.SPLIT_FLAP -> current.copy(
                preset = style, clockKind = ClockKind.DIGITAL, fontKey = "inter_tight",
                timeColor = 0xFFF2F2F2, dateColor = 0xFFB0B0B0, accentColor = 0xFFFFC400,
                backgroundColor = 0xFF27272B, backgroundAlpha = 255, cornerRadiusDp = 10f,
                glowEnabled = false, shadowEnabled = true, shadowDx = 0f, shadowDy = 2f,
                shadowRadius = 3f, gradientEnabled = false, frostedGlass = false,
                borderEnabled = false, letterSpacing = 0f, showSeconds = false
            )

            // Nixie digits are warm orange on near-black; a big glow radius is the point of
            // the style rather than an accident.
            ClockStyle.NIXIE_TUBE -> current.copy(
                preset = style, clockKind = ClockKind.DIGITAL, fontKey = "dseg7_classic",
                timeColor = 0xFFFF9B3D, dateColor = 0xFFB4702F, accentColor = 0xFFFFC98A,
                backgroundColor = 0xFF140C06, backgroundAlpha = 255, cornerRadiusDp = 18f,
                glowEnabled = true, glowRadius = 20f, shadowEnabled = false,
                gradientEnabled = false, frostedGlass = false, borderEnabled = false
            )

            // Dark grey on an olive backlight, no glow: an LCD is reflective, it never emits.
            ClockStyle.LCD_SEGMENT -> current.copy(
                preset = style, clockKind = ClockKind.DIGITAL, fontKey = "dseg7_classic",
                timeColor = 0xFF1C2410, dateColor = 0xFF39451F, accentColor = 0xFF1C2410,
                backgroundColor = 0xFFA9BA7F, backgroundAlpha = 255, cornerRadiusDp = 8f,
                glowEnabled = false, shadowEnabled = false, gradientEnabled = false,
                frostedGlass = false, borderEnabled = true, borderColor = 0xFF5A6340,
                borderWidthDp = 2f, letterSpacing = 0.06f
            )

            // The 5x7 bitmap font is drawn dot by dot, so fontKey is irrelevant for the time;
            // it still applies to the info line underneath.
            ClockStyle.LED_MATRIX -> current.copy(
                preset = style, clockKind = ClockKind.DIGITAL, fontKey = "led_dot_matrix",
                timeColor = 0xFFFF2D2D, dateColor = 0xFFB71C1C, accentColor = 0xFFFF2D2D,
                backgroundColor = 0xFF060608, backgroundAlpha = 255, cornerRadiusDp = 6f,
                glowEnabled = true, glowRadius = 9f, shadowEnabled = false,
                gradientEnabled = false, frostedGlass = false, borderEnabled = false,
                showSeconds = false
            )

            // Phosphor green; the amber variant is one colour-picker tap away.
            ClockStyle.RETRO_TERMINAL -> current.copy(
                preset = style, clockKind = ClockKind.DIGITAL, fontKey = "jetbrains_mono",
                timeColor = 0xFF33FF66, dateColor = 0xFF1F9940, accentColor = 0xFF33FF66,
                backgroundColor = 0xFF061006, backgroundAlpha = 255, cornerRadiusDp = 14f,
                glowEnabled = true, glowRadius = 12f, shadowEnabled = false,
                gradientEnabled = false, frostedGlass = false, borderEnabled = true,
                borderColor = 0xFF1F2B1F, borderWidthDp = 2f, use24Hour = true,
                showSeconds = true, letterSpacing = 0.06f
            )
        }
    }

    fun setLayout(layout: WidgetLayout) = _config.update { it.copy(layout = layout) }

    fun setTapAction(region: Int, action: TapAction) = _config.update {
        when (region) {
            1 -> it.copy(tapHours = action)
            2 -> it.copy(tapMinutes = action)
            3 -> it.copy(tapDate = action)
            else -> it.copy(tapWeather = action)
        }
    }

    fun save(onSaved: () -> Unit) = viewModelScope.launch {
        repository.saveConfig(_config.value)
        widgetUpdater.refresh(_config.value.appWidgetId)
        onSaved()
    }
}
