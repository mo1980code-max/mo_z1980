package com.digitalclockpro.presentation.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.ClockStyle
import com.digitalclockpro.domain.model.TapAction
import com.digitalclockpro.domain.model.WidgetConfig
import com.digitalclockpro.domain.model.WidgetLayout
import com.digitalclockpro.presentation.common.rememberCurrentTime
import com.digitalclockpro.widget.FontCatalog
import androidx.compose.ui.res.stringResource
import com.digitalclockpro.R
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import com.digitalclockpro.domain.model.AnalogFace
import com.digitalclockpro.domain.model.ClockKind
import com.digitalclockpro.domain.model.HandMotion
import com.digitalclockpro.presentation.common.AnalogClock

/** Tab titles as resource ids; resolved with `stringResource` inside the composable. */
private val TAB_TITLES = listOf(
    R.string.tab_presets,
    R.string.tab_font,
    R.string.tab_colors,
    R.string.tab_background,
    R.string.tab_layout,
    R.string.tab_tap_actions
)

/**
 * Widget Customizer Studio: a live WYSIWYG preview pinned to the top with a tabbed editor below.
 * Every edit mutates the in-memory [WidgetConfig], so the preview updates instantly; nothing is
 * persisted until stringResource(R.string.save_widget).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetStudioScreen(
    appWidgetId: Int,
    onSaved: () -> Unit,
    viewModel: WidgetStudioViewModel = hiltViewModel()
) {
    LaunchedEffect(appWidgetId) { viewModel.load(appWidgetId) }
    val config by viewModel.config.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        LivePreview(config, Modifier.padding(16.dp))

        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 12.dp) {
            TAB_TITLES.forEachIndexed { index, titleRes ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(stringResource(titleRes)) })
            }
        }

        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            when (tab) {
                0 -> PresetsTab(config, viewModel)
                1 -> FontTab(config, viewModel)
                2 -> ColorsTab(config, viewModel)
                3 -> BackgroundTab(config, viewModel)
                4 -> LayoutTab(config, viewModel)
                else -> TapActionsTab(config, viewModel)
            }
        }

        Button(
            onClick = { viewModel.save(onSaved) },
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) { Text(stringResource(R.string.save_widget)) }
    }
}

/** Compose approximation of the RemoteViews output – same colors, fonts sizes and content. */
@Composable
private fun LivePreview(config: WidgetConfig, modifier: Modifier = Modifier) {
    val now by rememberCurrentTime(withSeconds = config.showSeconds)

    if (config.clockKind == ClockKind.ANALOG) {
        Box(
            modifier = modifier.fillMaxWidth().height(220.dp),
            contentAlignment = Alignment.Center
        ) {
            AnalogClock(
                face = config.analogFace,
                modifier = Modifier.size(200.dp),
                // The preview always sweeps so the user can judge the motion they picked.
                motion = config.handMotion,
                showSeconds = config.showSecondHand,
                showNumerals = config.showAnalogNumerals,
                digitalInsetText = TimeFormatters.formatTime(now, config.use24Hour, false)
            )
        }
        return
    }

    val background = Color(config.backgroundColor).copy(alpha = config.backgroundAlpha / 255f)
    val timeBrush = if (config.gradientEnabled) {
        Brush.horizontalGradient(listOf(Color(config.timeColor), Color(config.gradientEndColor)))
    } else {
        Brush.horizontalGradient(listOf(Color(config.timeColor), Color(config.timeColor)))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height((config.layout.rows * 60 + 40).dp)
            .clip(RoundedCornerShape(config.cornerRadiusDp.dp))
            .background(background)
            .then(
                if (config.borderEnabled) Modifier.border(
                    config.borderWidthDp.dp,
                    Color(config.borderColor),
                    RoundedCornerShape(config.cornerRadiusDp.dp)
                ) else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = TimeFormatters.formatTime(now, config.use24Hour, config.showSeconds),
                    fontSize = config.timeTextSizeSp.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.ui.text.TextStyle(brush = timeBrush)
                )
                if (!config.use24Hour && config.showAmPm) {
                    Text(
                        "  " + TimeFormatters.amPm(now),
                        fontSize = (config.timeTextSizeSp * 0.3f).sp,
                        color = Color(config.accentColor)
                    )
                }
            }
            val info = buildList {
                if (config.showDate) add(TimeFormatters.formatDate(now, config.datePattern))
                if (config.showBattery && !config.batteryAsBar) add(stringResource(R.string.studio_preview_battery))
                if (config.showNextAlarm) add(stringResource(R.string.studio_preview_alarm))
                if (config.showWeather) add(if (config.weatherUnitCelsius) stringResource(R.string.studio_preview_weather_c) else stringResource(R.string.studio_preview_weather_f))
            }
            if (info.isNotEmpty()) {
                Text(
                    info.joinToString("  •  "),
                    fontSize = config.dateTextSizeSp.sp,
                    color = Color(config.dateColor)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresetsTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    // Kind first: digital and analog have entirely different preset catalogues.
    Text(stringResource(R.string.studio_clock_kind), style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        ClockKind.entries.forEachIndexed { index, kind ->
            SegmentedButton(
                selected = config.clockKind == kind,
                onClick = { vm.setClockKind(kind) },
                shape = SegmentedButtonDefaults.itemShape(index, ClockKind.entries.size)
            ) {
                Text(
                    stringResource(
                        if (kind == ClockKind.DIGITAL) R.string.studio_kind_digital
                        else R.string.studio_kind_analog
                    )
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))

    if (config.clockKind == ClockKind.DIGITAL) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ClockStyle.entries.forEach { style ->
                FilterChip(
                    selected = config.preset == style,
                    onClick = { vm.applyPreset(style) },
                    label = { Text(style.displayName) }
                )
            }
        }
    } else {
        AnalogFacesSection(config, vm)
    }
}

/** The six bundled dials, each with a live Compose preview of the real face. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AnalogFacesSection(config: WidgetConfig, vm: WidgetStudioViewModel) {
    Text(stringResource(R.string.studio_analog_face), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnalogFace.entries.forEach { face ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(vertical = 6.dp)
                    .clickable { vm.setAnalogFace(face) }
            ) {
                Box(
                    Modifier
                        .size(72.dp)
                        .then(
                            if (config.analogFace == face) Modifier.border(
                                2.dp, MaterialTheme.colorScheme.primary, CircleShape
                            ) else Modifier
                        )
                        .padding(3.dp)
                ) {
                    AnalogClock(
                        face = face,
                        modifier = Modifier.fillMaxSize(),
                        motion = HandMotion.QUARTZ,
                        showSeconds = config.showSecondHand,
                        showNumerals = config.showAnalogNumerals
                    )
                }
                Text(
                    text = stringResource(analogFaceLabel(face)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.studio_hand_motion), style = MaterialTheme.typography.titleSmall)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        HandMotion.entries.forEachIndexed { index, motion ->
            SegmentedButton(
                selected = config.handMotion == motion,
                onClick = { vm.update { it.copy(handMotion = motion) } },
                shape = SegmentedButtonDefaults.itemShape(index, HandMotion.entries.size)
            ) {
                Text(
                    stringResource(
                        if (motion == HandMotion.QUARTZ) R.string.studio_motion_quartz
                        else R.string.studio_motion_smooth
                    )
                )
            }
        }
    }
    Text(
        text = stringResource(R.string.studio_motion_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    SwitchRow(stringResource(R.string.studio_show_second_hand), config.showSecondHand) {
        vm.update { c -> c.copy(showSecondHand = it) }
    }
    SwitchRow(stringResource(R.string.studio_show_numerals), config.showAnalogNumerals) {
        vm.update { c -> c.copy(showAnalogNumerals = it) }
    }
}

@StringRes
private fun analogFaceLabel(face: AnalogFace): Int = when (face) {
    AnalogFace.SWISS_MINIMAL -> R.string.face_swiss_minimal
    AnalogFace.CLASSIC_ROMAN -> R.string.face_classic_roman
    AnalogFace.LUXURY_GOLD -> R.string.face_luxury_gold
    AnalogFace.NEON_ANALOG -> R.string.face_neon_analog
    AnalogFace.STEAMPUNK -> R.string.face_steampunk
    AnalogFace.DIGITAL_HYBRID -> R.string.face_digital_hybrid
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    Text(stringResource(R.string.studio_bundled_fonts, FontCatalog.fonts.size), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FontCatalog.fonts.forEach { font ->
            FilterChip(
                selected = config.fontKey == font.key && config.customFontUri == null,
                onClick = { vm.update { it.copy(fontKey = font.key, customFontUri = null) } },
                label = { Text(font.displayName) }
            )
        }
    }
    LabeledSlider(stringResource(R.string.studio_time_size, config.timeTextSizeSp.toInt()), config.timeTextSizeSp, 16f, 140f) {
        vm.update { c -> c.copy(timeTextSizeSp = it) }
    }
    LabeledSlider(stringResource(R.string.studio_date_size, config.dateTextSizeSp.toInt()), config.dateTextSizeSp, 8f, 32f) {
        vm.update { c -> c.copy(dateTextSizeSp = it) }
    }
    LabeledSlider(stringResource(R.string.studio_letter_spacing), config.letterSpacing, -0.05f, 0.4f) {
        vm.update { c -> c.copy(letterSpacing = it) }
    }
}

@Composable
private fun ColorsTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    HexColorField(stringResource(R.string.studio_time_color), config.timeColor) { vm.update { c -> c.copy(timeColor = it) } }
    HexColorField(stringResource(R.string.studio_date_color), config.dateColor) { vm.update { c -> c.copy(dateColor = it) } }
    HexColorField(stringResource(R.string.studio_accent_color), config.accentColor) { vm.update { c -> c.copy(accentColor = it) } }
    SwitchRow(stringResource(R.string.studio_gradient), config.gradientEnabled) { vm.update { c -> c.copy(gradientEnabled = it) } }
    if (config.gradientEnabled) {
        HexColorField(stringResource(R.string.studio_gradient_end), config.gradientEndColor) {
            vm.update { c -> c.copy(gradientEndColor = it) }
        }
    }
    SwitchRow(stringResource(R.string.studio_glow), config.glowEnabled) { vm.update { c -> c.copy(glowEnabled = it) } }
    if (config.glowEnabled) {
        LabeledSlider(stringResource(R.string.studio_glow_radius), config.glowRadius, 1f, 40f) {
            vm.update { c -> c.copy(glowRadius = it) }
        }
    }
    SwitchRow(stringResource(R.string.studio_shadow), config.shadowEnabled) { vm.update { c -> c.copy(shadowEnabled = it) } }
    if (config.shadowEnabled) {
        LabeledSlider(stringResource(R.string.studio_shadow_dx), config.shadowDx, -10f, 10f) { vm.update { c -> c.copy(shadowDx = it) } }
        LabeledSlider(stringResource(R.string.studio_shadow_dy), config.shadowDy, -10f, 10f) { vm.update { c -> c.copy(shadowDy = it) } }
        LabeledSlider(stringResource(R.string.studio_shadow_blur), config.shadowRadius, 0f, 20f) {
            vm.update { c -> c.copy(shadowRadius = it) }
        }
    }
    SwitchRow(stringResource(R.string.studio_dynamic_color), config.useDynamicColor) {
        vm.update { c -> c.copy(useDynamicColor = it) }
    }
}

@Composable
private fun BackgroundTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    HexColorField(stringResource(R.string.studio_background), config.backgroundColor) {
        vm.update { c -> c.copy(backgroundColor = it) }
    }
    LabeledSlider(stringResource(R.string.studio_opacity, config.backgroundAlpha * 100 / 255), config.backgroundAlpha.toFloat(), 0f, 255f) {
        vm.update { c -> c.copy(backgroundAlpha = it.toInt()) }
    }
    LabeledSlider(stringResource(R.string.studio_corner_radius, config.cornerRadiusDp.toInt()), config.cornerRadiusDp, 0f, 48f) {
        vm.update { c -> c.copy(cornerRadiusDp = it) }
    }
    SwitchRow(stringResource(R.string.studio_frosted), config.frostedGlass) { vm.update { c -> c.copy(frostedGlass = it) } }
    SwitchRow(stringResource(R.string.studio_border), config.borderEnabled) { vm.update { c -> c.copy(borderEnabled = it) } }
    if (config.borderEnabled) {
        HexColorField(stringResource(R.string.studio_border_color), config.borderColor) { vm.update { c -> c.copy(borderColor = it) } }
        LabeledSlider(stringResource(R.string.studio_border_width), config.borderWidthDp, 0.5f, 6f) {
            vm.update { c -> c.copy(borderWidthDp = it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    Text(stringResource(R.string.studio_size), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetLayout.entries.forEach { layout ->
            FilterChip(
                selected = config.layout == layout,
                onClick = { vm.setLayout(layout) },
                label = { Text(layout.label) }
            )
        }
    }
    SwitchRow(stringResource(R.string.studio_24h), config.use24Hour) { vm.update { c -> c.copy(use24Hour = it) } }
    SwitchRow(stringResource(R.string.studio_show_seconds), config.showSeconds) { vm.update { c -> c.copy(showSeconds = it) } }
    SwitchRow(stringResource(R.string.studio_show_ampm), config.showAmPm) { vm.update { c -> c.copy(showAmPm = it) } }
    SwitchRow(stringResource(R.string.studio_show_date), config.showDate) { vm.update { c -> c.copy(showDate = it) } }
    if (config.showDate) {
        Text(stringResource(R.string.studio_date_format), style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TimeFormatters.commonDatePatterns.forEach { pattern ->
                FilterChip(
                    selected = config.datePattern == pattern,
                    onClick = { vm.update { c -> c.copy(datePattern = pattern) } },
                    label = { Text(pattern) }
                )
            }
        }
    }
    SwitchRow(stringResource(R.string.studio_battery), config.showBattery) { vm.update { c -> c.copy(showBattery = it) } }
    if (config.showBattery) {
        SwitchRow(stringResource(R.string.studio_battery_bar), config.batteryAsBar) { vm.update { c -> c.copy(batteryAsBar = it) } }
    }
    SwitchRow(stringResource(R.string.studio_next_alarm), config.showNextAlarm) { vm.update { c -> c.copy(showNextAlarm = it) } }
    SwitchRow(stringResource(R.string.studio_weather), config.showWeather) { vm.update { c -> c.copy(showWeather = it) } }
    if (config.showWeather) {
        SwitchRow(stringResource(R.string.studio_celsius), config.weatherUnitCelsius) {
            vm.update { c -> c.copy(weatherUnitCelsius = it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TapActionsTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    val regions = listOf(
        Triple(1, stringResource(R.string.studio_tap_hours), config.tapHours),
        Triple(2, stringResource(R.string.studio_tap_minutes), config.tapMinutes),
        Triple(3, stringResource(R.string.studio_tap_date), config.tapDate),
        Triple(4, stringResource(R.string.studio_tap_weather), config.tapWeather)
    )
    regions.forEach { (region, title, current) ->
        Text(title, style = MaterialTheme.typography.titleSmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TapAction.entries.forEach { action ->
                FilterChip(
                    selected = current == action,
                    onClick = { vm.setTapAction(region, action) },
                    label = { Text(action.name.removePrefix("OPEN_").lowercase().replace('_', ' ')) }
                )
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    onChange: (Float) -> Unit
) {
    Column {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value.coerceIn(min, max), onValueChange = onChange, valueRange = min..max)
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Hex colour picker with live swatch; accepts #AARRGGBB or #RRGGBB. */
@Composable
private fun HexColorField(label: String, value: Long, onChange: (Long) -> Unit) {
    var text by remember(value) { mutableStateOf("#%08X".format(value)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(value))
        )
        Spacer(Modifier.width(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                val cleaned = input.removePrefix("#")
                val parsed = cleaned.toLongOrNull(16) ?: return@OutlinedTextField
                onChange(if (cleaned.length <= 6) 0xFF000000L or parsed else parsed)
            },
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )
    }
}
