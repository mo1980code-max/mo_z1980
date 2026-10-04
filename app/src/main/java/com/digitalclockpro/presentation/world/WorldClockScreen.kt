package com.digitalclockpro.presentation.world

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.core.util.TimeFormatters
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
    val liveNow by rememberCurrentTime(withSeconds = false)

    // Time-travel: the whole screen renders at "now + offset".
    val reference = liveNow.plusMinutes(state.travelOffsetMinutes.toLong())

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            placeholder = { Text("Search 10,000+ cities…") },
            singleLine = true
        )

        if (results.isNotEmpty()) {
            LazyColumn(Modifier.weight(1f)) {
                items(results, key = { it.cityName + it.zoneId }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.cityName) },
                        supportingContent = { Text("${entry.country} • ${entry.zoneId}") },
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.cities, key = { it.id }) { city ->
                CityCard(
                    city = city,
                    reference = reference,
                    homeZone = state.homeZone,
                    use24Hour = state.use24Hour,
                    onRemove = { viewModel.onRemoveCity(city.id) }
                )
            }
        }
    }
}

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
                text = if (offsetMinutes == 0) "Now"
                else buildString {
                    append(if (offsetMinutes > 0) "+" else "−")
                    append("${abs(offsetMinutes) / 60}h ")
                    append("${abs(offsetMinutes) % 60}m")
                } + "  →  " + TimeFormatters.formatTime(reference, use24Hour, false),
                style = MaterialTheme.typography.titleMedium
            )
            TextButton(onClick = onReset, enabled = offsetMinutes != 0) { Text("Reset") }
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
    onRemove: () -> Unit
) {
    val local = city.nowAt(reference)
    val day = city.isDaytime(reference)
    val gradient = if (day) {
        Brush.horizontalGradient(listOf(Color(0xFF1E88E5), Color(0xFF42A5F5)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF1A237E), Color(0xFF311B92)))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(gradient)
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (day) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                        contentDescription = if (day) "Daytime" else "Night",
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
                    "${city.utcOffsetLabel(reference)} • ${city.dayLabel(homeZone, reference)} • " +
                        city.relativeLabel(homeZone, reference),
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
                Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = Color.White)
            }
        }
    }
}
