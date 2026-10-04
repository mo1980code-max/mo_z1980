package com.digitalclockpro.presentation.world

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.R
import com.digitalclockpro.clockengine.DayNight
import com.digitalclockpro.clockengine.MeetingPlanner
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.core.util.WorldClockLabels
import com.digitalclockpro.domain.model.SavedCity
import com.digitalclockpro.presentation.common.rememberCurrentTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

@Composable
fun WorldClockScreen(viewModel: WorldClockViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val comparison by viewModel.comparison.collectAsStateWithLifecycle()
    val comparisonIds by viewModel.comparisonIds.collectAsStateWithLifecycle()
    val plan by viewModel.meetingPlan.collectAsStateWithLifecycle()
    val liveNow by rememberCurrentTime(withSeconds = false)

    var showPlanner by remember { mutableStateOf(false) }

    // Time-travel: the whole screen renders at "now + offset".
    val reference = liveNow.plusMinutes(state.travelOffsetMinutes.toLong())

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.search_cities)) },
            singleLine = true
        )

        if (results.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.cityName + it.zoneId }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.cityName) },
                        supportingContent = {
                            Text(stringResource(R.string.world_city_subtitle, entry.country, entry.zoneId))
                        },
                        modifier = Modifier.clickable { viewModel.onAddCity(entry) }
                    )
                }
            }
            return@Column
        }

        TimeTravelSlider(
            offsetMinutes = state.travelOffsetMinutes,
            onOffsetChange = viewModel::onTravelOffsetChange,
            onReset = viewModel::resetTravel,
            reference = reference,
            use24Hour = state.use24Hour
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (comparison.size >= 2) {
                item(key = "comparison") {
                    ComparisonRow(
                        cities = comparison,
                        reference = reference,
                        homeZone = state.homeZone,
                        use24Hour = state.use24Hour
                    )
                }
            }

            item(key = "planner-toggle") {
                AssistChip(
                    onClick = { showPlanner = !showPlanner },
                    leadingIcon = { Icon(Icons.Filled.Groups, contentDescription = null) },
                    label = {
                        Text(
                            stringResource(
                                if (showPlanner) R.string.meeting_hide else R.string.meeting_show
                            )
                        )
                    }
                )
            }

            if (showPlanner) {
                item(key = "planner") {
                    MeetingPlannerSection(
                        plan = plan,
                        cities = state.cities,
                        selectedIds = comparisonIds,
                        homeZone = state.homeZone,
                        use24Hour = state.use24Hour,
                        onToggleCity = viewModel::toggleComparison,
                        onShiftDay = viewModel::shiftPlannerDay,
                        onWorkingHours = viewModel::setWorkingHours
                    )
                }
            }

            items(state.cities, key = { it.id }) { city ->
                CityCard(
                    city = city,
                    reference = reference,
                    homeZone = state.homeZone,
                    use24Hour = state.use24Hour,
                    selected = city.id in comparisonIds,
                    onToggleCompare = { viewModel.toggleComparison(city.id) },
                    onRemove = { viewModel.onRemoveCity(city.id) }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- day/night palette

/**
 * The gradient for a city card, interpolated from its real solar position.
 *
 * Each phase has its own two-stop palette, and [DayNight.daylightFraction] then blends the DAY
 * palette in on top, so a card drifts smoothly from dawn pink through midday blue to indigo
 * night instead of snapping between six fixed looks.
 */
@Composable
private fun cityGradient(city: SavedCity, reference: ZonedDateTime): Brush {
    val phase = city.dayPhase(reference)
    val fraction = city.daylightFraction(reference)

    val (start, end) = when (phase) {
        DayNight.Phase.NIGHT -> Color(0xFF0D1B3E) to Color(0xFF231A4D)
        DayNight.Phase.DAWN -> Color(0xFF3B3160) to Color(0xFF8C5A7A)
        DayNight.Phase.SUNRISE -> Color(0xFFE8703A) to Color(0xFFF2A65A)
        DayNight.Phase.DAY -> Color(0xFF1E88E5) to Color(0xFF64B5F6)
        DayNight.Phase.SUNSET -> Color(0xFFD1495B) to Color(0xFFE8833A)
        DayNight.Phase.DUSK -> Color(0xFF4A3A72) to Color(0xFF2B2A5E)
    }

    val dayStart = Color(0xFF1E88E5)
    val dayEnd = Color(0xFF64B5F6)
    return Brush.horizontalGradient(
        listOf(
            lerp(start, dayStart, fraction * 0.45f),
            lerp(end, dayEnd, fraction * 0.45f)
        )
    )
}

// ---------------------------------------------------------------------- comparison row

/**
 * Two to four cities shown side by side.
 *
 * The offset pill is colour-coded by daylight (green = working daytime, blue = night) because
 * that is the single thing you actually scan for: "can I call them right now?".
 */
@Composable
private fun ComparisonRow(
    cities: List<SavedCity>,
    reference: ZonedDateTime,
    homeZone: ZoneId,
    use24Hour: Boolean
) {
    Column {
        Text(
            text = stringResource(R.string.world_comparison_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            cities.forEach { city ->
                ComparisonCard(city, reference, homeZone, use24Hour)
            }
        }
    }
}

@Composable
private fun ComparisonCard(
    city: SavedCity,
    reference: ZonedDateTime,
    homeZone: ZoneId,
    use24Hour: Boolean
) {
    val local = city.nowAt(reference)
    val day = city.isDaytime(reference)
    val offsetMinutes = city.offsetMinutesFrom(homeZone, reference)

    val pill = if (day) Color(0xFF2E7D32) else Color(0xFF1565C0)

    Column(
        modifier = Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cityGradient(city, reference))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = if (day) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = stringResource(
                if (day) R.string.world_daytime else R.string.world_night
            ),
            tint = Color.White
        )
        Text(
            text = city.cityName,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = TimeFormatters.formatTime(local, use24Hour, showSeconds = false),
            color = Color.White,
            fontFamily = FontFamily.Monospace,
            fontSize = 20.sp
        )
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(pill)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = WorldClockLabels.shortOffset(offsetMinutes),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

// ---------------------------------------------------------------------- meeting planner

@Composable
private fun MeetingPlannerSection(
    plan: MeetingPlanState,
    cities: List<SavedCity>,
    selectedIds: Set<Long>,
    homeZone: ZoneId,
    use24Hour: Boolean,
    onToggleCity: (Long) -> Unit,
    onShiftDay: (Long) -> Unit,
    onWorkingHours: (Int, Int) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.meeting_title),
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                stringResource(
                    R.string.meeting_subtitle,
                    plan.workingHours.startHour,
                    plan.workingHours.endHour
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(10.dp))

            // City picker
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cities.forEach { city ->
                    FilterChip(
                        selected = city.id in selectedIds,
                        onClick = { onToggleCity(city.id) },
                        label = { Text(city.cityName, maxLines = 1) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Working-hours presets. A free numeric input here would be three taps slower and
            // invite nonsense like 25:00.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(8 to 16, 9 to 17, 10 to 18).forEach { (start, end) ->
                    FilterChip(
                        selected = plan.workingHours.startHour == start &&
                            plan.workingHours.endHour == end,
                        onClick = { onWorkingHours(start, end) },
                        label = { Text(stringResource(R.string.meeting_hours_range, start, end)) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onShiftDay(-1L) }) {
                    Text(stringResource(R.string.meeting_previous_day))
                }
                Text(
                    text = plan.day.toString(),
                    style = MaterialTheme.typography.labelLarge
                )
                TextButton(onClick = { onShiftDay(1L) }) {
                    Text(stringResource(R.string.meeting_next_day))
                }
            }

            Spacer(Modifier.height(8.dp))

            when {
                !plan.hasEnoughCities -> Text(
                    stringResource(R.string.meeting_need_cities),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                plan.windows.isEmpty() -> Text(
                    stringResource(R.string.meeting_no_window),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )

                else -> {
                    if (!plan.hasPerfectWindow) {
                        Text(
                            stringResource(R.string.meeting_partial_warning),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    plan.windows.forEach { window ->
                        MeetingWindowRow(window, homeZone, use24Hour)
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MeetingWindowRow(
    window: MeetingPlanner.Window,
    homeZone: ZoneId,
    use24Hour: Boolean
) {
    val start = window.startUtc.atZone(homeZone)
    val end = window.endUtc.atZone(homeZone)
    val accent = if (window.isUnanimous) Color(0xFF2E7D32) else Color(0xFFEF6C00)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, accent, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = TimeFormatters.formatTime(start, use24Hour, showSeconds = false) +
                "  –  " + TimeFormatters.formatTime(end, use24Hour, showSeconds = false),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.titleSmall
        )
        Text(
            text = stringResource(
                R.string.meeting_window_meta,
                window.durationMinutes / 60,
                window.durationMinutes % 60,
                window.availableCount,
                window.totalParticipants
            ),
            style = MaterialTheme.typography.labelSmall,
            color = accent
        )
    }
}

// ---------------------------------------------------------------------- existing pieces

@Composable
private fun TimeTravelSlider(
    offsetMinutes: Int,
    onOffsetChange: (Int) -> Unit,
    onReset: () -> Unit,
    reference: ZonedDateTime,
    use24Hour: Boolean
) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (offsetMinutes == 0) {
                    stringResource(R.string.reset_to_now)
                } else {
                    val sign = if (offsetMinutes > 0) "+" else "\u2212"
                    val hours = stringResource(R.string.world_offset_hours, abs(offsetMinutes) / 60)
                    val minutes =
                        stringResource(R.string.world_offset_minutes, abs(offsetMinutes) % 60)
                    val time = TimeFormatters.formatTime(reference, use24Hour, false)
                    "$sign$hours $minutes  \u2192  $time"
                },
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onReset, enabled = offsetMinutes != 0) {
                Text(stringResource(R.string.reset))
            }
        }
        Slider(
            value = offsetMinutes.toFloat(),
            onValueChange = { onOffsetChange(((it / 15).toInt()) * 15) },  // 15-minute steps
            valueRange = -1440f..1440f,
            steps = 191
        )
    }
}

@Composable
private fun CityCard(
    city: SavedCity,
    reference: ZonedDateTime,
    homeZone: ZoneId,
    use24Hour: Boolean,
    selected: Boolean,
    onToggleCompare: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val local = city.nowAt(reference)
    val day = city.isDaytime(reference)

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(cityGradient(city, reference))
                .then(
                    if (selected) Modifier.border(2.dp, Color.White, RoundedCornerShape(16.dp))
                    else Modifier
                )
                .fillMaxWidth()
                .clickable(onClick = onToggleCompare)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (day) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = stringResource(
                            if (day) R.string.world_daytime else R.string.world_night
                        ),
                        tint = Color.White
                    )
                    Text(
                        "  ${city.cityName}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOf(
                        city.utcOffsetLabel(reference),
                        WorldClockLabels.dayOffset(context, city, homeZone, reference),
                        WorldClockLabels.difference(context, city.difference(homeZone, reference))
                    ).joinToString(stringResource(R.string.bullet_separator)),
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                TimeFormatters.formatTime(local, use24Hour, showSeconds = false),
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 28.sp
            )
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.remove),
                    tint = Color.White
                )
            }
        }
    }
}
