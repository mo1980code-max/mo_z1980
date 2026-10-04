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

private val TABS = listOf("Presets", "Font", "Colors", "Background", "Layout", "Tap actions")

/**
 * Widget Customizer Studio: a live WYSIWYG preview pinned to the top with a tabbed editor below.
 * Every edit mutates the in-memory [WidgetConfig], so the preview updates instantly; nothing is
 * persisted until "Save widget".
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
            TABS.forEachIndexed { index, title ->
                Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
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
        ) { Text("Save widget") }
    }
}

/** Compose approximation of the RemoteViews output – same colors, fonts sizes and content. */
@Composable
private fun LivePreview(config: WidgetConfig, modifier: Modifier = Modifier) {
    val now by rememberCurrentTime(withSeconds = config.showSeconds)
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
                if (config.showBattery && !config.batteryAsBar) add("87%")
                if (config.showNextAlarm) add("⏰ 7:00 AM")
                if (config.showWeather) add(if (config.weatherUnitCelsius) "24°C Clear" else "75°F Clear")
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockStyle.entries.forEach { style ->
            FilterChip(
                selected = config.preset == style,
                onClick = { vm.applyPreset(style) },
                label = { Text(style.displayName) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    Text("Bundled fonts (${FontCatalog.fonts.size})", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FontCatalog.fonts.forEach { font ->
            FilterChip(
                selected = config.fontKey == font.key && config.customFontUri == null,
                onClick = { vm.update { it.copy(fontKey = font.key, customFontUri = null) } },
                label = { Text(font.displayName) }
            )
        }
    }
    LabeledSlider("Time size: ${config.timeTextSizeSp.toInt()}sp", config.timeTextSizeSp, 16f, 140f) {
        vm.update { c -> c.copy(timeTextSizeSp = it) }
    }
    LabeledSlider("Date size: ${config.dateTextSizeSp.toInt()}sp", config.dateTextSizeSp, 8f, 32f) {
        vm.update { c -> c.copy(dateTextSizeSp = it) }
    }
    LabeledSlider("Letter spacing", config.letterSpacing, -0.05f, 0.4f) {
        vm.update { c -> c.copy(letterSpacing = it) }
    }
}

@Composable
private fun ColorsTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    HexColorField("Time color", config.timeColor) { vm.update { c -> c.copy(timeColor = it) } }
    HexColorField("Date color", config.dateColor) { vm.update { c -> c.copy(dateColor = it) } }
    HexColorField("Accent color", config.accentColor) { vm.update { c -> c.copy(accentColor = it) } }
    SwitchRow("Gradient", config.gradientEnabled) { vm.update { c -> c.copy(gradientEnabled = it) } }
    if (config.gradientEnabled) {
        HexColorField("Gradient end", config.gradientEndColor) {
            vm.update { c -> c.copy(gradientEndColor = it) }
        }
    }
    SwitchRow("Neon glow", config.glowEnabled) { vm.update { c -> c.copy(glowEnabled = it) } }
    if (config.glowEnabled) {
        LabeledSlider("Glow radius", config.glowRadius, 1f, 40f) {
            vm.update { c -> c.copy(glowRadius = it) }
        }
    }
    SwitchRow("Drop shadow", config.shadowEnabled) { vm.update { c -> c.copy(shadowEnabled = it) } }
    if (config.shadowEnabled) {
        LabeledSlider("Shadow dx", config.shadowDx, -10f, 10f) { vm.update { c -> c.copy(shadowDx = it) } }
        LabeledSlider("Shadow dy", config.shadowDy, -10f, 10f) { vm.update { c -> c.copy(shadowDy = it) } }
        LabeledSlider("Shadow blur", config.shadowRadius, 0f, 20f) {
            vm.update { c -> c.copy(shadowRadius = it) }
        }
    }
    SwitchRow("Follow Material You colors", config.useDynamicColor) {
        vm.update { c -> c.copy(useDynamicColor = it) }
    }
}

@Composable
private fun BackgroundTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    HexColorField("Background", config.backgroundColor) {
        vm.update { c -> c.copy(backgroundColor = it) }
    }
    LabeledSlider("Opacity: ${(config.backgroundAlpha * 100 / 255)}%", config.backgroundAlpha.toFloat(), 0f, 255f) {
        vm.update { c -> c.copy(backgroundAlpha = it.toInt()) }
    }
    LabeledSlider("Corner radius: ${config.cornerRadiusDp.toInt()}dp", config.cornerRadiusDp, 0f, 48f) {
        vm.update { c -> c.copy(cornerRadiusDp = it) }
    }
    SwitchRow("Frosted glass", config.frostedGlass) { vm.update { c -> c.copy(frostedGlass = it) } }
    SwitchRow("Border", config.borderEnabled) { vm.update { c -> c.copy(borderEnabled = it) } }
    if (config.borderEnabled) {
        HexColorField("Border color", config.borderColor) { vm.update { c -> c.copy(borderColor = it) } }
        LabeledSlider("Border width", config.borderWidthDp, 0.5f, 6f) {
            vm.update { c -> c.copy(borderWidthDp = it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LayoutTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    Text("Size", style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WidgetLayout.entries.forEach { layout ->
            FilterChip(
                selected = config.layout == layout,
                onClick = { vm.setLayout(layout) },
                label = { Text(layout.label) }
            )
        }
    }
    SwitchRow("24-hour format", config.use24Hour) { vm.update { c -> c.copy(use24Hour = it) } }
    SwitchRow("Show seconds", config.showSeconds) { vm.update { c -> c.copy(showSeconds = it) } }
    SwitchRow("Show AM/PM", config.showAmPm) { vm.update { c -> c.copy(showAmPm = it) } }
    SwitchRow("Show date", config.showDate) { vm.update { c -> c.copy(showDate = it) } }
    if (config.showDate) {
        Text("Date format", style = MaterialTheme.typography.titleSmall)
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
    SwitchRow("Battery indicator", config.showBattery) { vm.update { c -> c.copy(showBattery = it) } }
    if (config.showBattery) {
        SwitchRow("Show as bar", config.batteryAsBar) { vm.update { c -> c.copy(batteryAsBar = it) } }
    }
    SwitchRow("Next alarm", config.showNextAlarm) { vm.update { c -> c.copy(showNextAlarm = it) } }
    SwitchRow("Weather", config.showWeather) { vm.update { c -> c.copy(showWeather = it) } }
    if (config.showWeather) {
        SwitchRow("Celsius", config.weatherUnitCelsius) {
            vm.update { c -> c.copy(weatherUnitCelsius = it) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TapActionsTab(config: WidgetConfig, vm: WidgetStudioViewModel) {
    val regions = listOf(
        Triple(1, "Tap hours", config.tapHours),
        Triple(2, "Tap minutes", config.tapMinutes),
        Triple(3, "Tap date", config.tapDate),
        Triple(4, "Tap weather", config.tapWeather)
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
