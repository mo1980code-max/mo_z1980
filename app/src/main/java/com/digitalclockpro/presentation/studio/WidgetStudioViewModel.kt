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
