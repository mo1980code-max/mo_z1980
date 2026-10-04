package com.digitalclockpro.presentation.timer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.R
import com.digitalclockpro.clockengine.DurationFormatter
import com.digitalclockpro.clockengine.StopwatchEngine
import com.digitalclockpro.clockengine.TimerEngine

/**
 * The "Timer" bottom-navigation destination: a countdown timer and a stopwatch behind two tabs,
 * sharing one [TimerViewModel] so both keep running when the user switches between them.
 */
@Composable
fun TimerTabScreen(viewModel: TimerViewModel = hiltViewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf(R.string.timer_tab_timer, R.string.timer_tab_stopwatch)

    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { index, titleRes ->
                Tab(
                    selected = tab == index,
                    onClick = { tab = index },
                    text = { Text(stringResource(titleRes)) }
                )
            }
        }
        when (tab) {
            0 -> TimerScreen(viewModel)
            else -> StopwatchScreen(viewModel)
        }
    }
}

// ---------------------------------------------------------------------- timer

@Composable
fun TimerScreen(viewModel: TimerViewModel = hiltViewModel()) {
    val state by viewModel.timer.collectAsStateWithLifecycle()
    val now by viewModel.nowElapsed.collectAsStateWithLifecycle()

    val remaining = state.remaining(now)
    val finished = state.phase == TimerEngine.Phase.FINISHED

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(0.72f).aspectRatio(1f),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                progress = { state.progress(now) },
                modifier = Modifier.fillMaxSize(),
                strokeWidth = 10.dp,
                color = if (finished) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = DurationFormatter.formatClock(remaining),
                    fontSize = 56.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (finished) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurface
                )
                if (finished) {
                    Text(
                        text = stringResource(R.string.timer_finished),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        if (finished) {
            Button(
                onClick = viewModel::dismissAlert,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.timer_stop_alert)) }
        } else {
            // Quick presets: by far the most common way people set a kitchen timer.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 30).forEach { minutes ->
                    AssistChip(
                        onClick = { viewModel.addMinutes(minutes) },
                        label = { Text(stringResource(R.string.timer_add_minutes, minutes)) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = viewModel::resetTimer,
                    modifier = Modifier.weight(1f),
                    enabled = state.phase != TimerEngine.Phase.IDLE || remaining > 0L
                ) { Text(stringResource(R.string.timer_reset)) }

                Button(
                    onClick = viewModel::startOrPause,
                    modifier = Modifier.weight(1f),
                    enabled = state.isRunning || state.canStart
                ) {
                    Text(
                        stringResource(
                            if (state.isRunning) R.string.timer_pause else R.string.timer_start
                        )
                    )
                }
            }

            if (remaining == 0L && !state.isRunning) {
                Text(
                    text = stringResource(R.string.timer_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- stopwatch

@Composable
fun StopwatchScreen(viewModel: TimerViewModel = hiltViewModel()) {
    val state by viewModel.stopwatch.collectAsStateWithLifecycle()
    val now by viewModel.nowElapsed.collectAsStateWithLifecycle()

    val fastest = remember(state.laps) { StopwatchEngine.fastestLapIndex(state.laps) }
    val slowest = remember(state.laps) { StopwatchEngine.slowestLapIndex(state.laps) }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = DurationFormatter.formatPrecise(state.elapsed(now)),
            fontSize = 56.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = if (state.running) viewModel::lap else viewModel::resetStopwatch,
                modifier = Modifier.weight(1f),
                enabled = state.started
            ) {
                Text(
                    stringResource(
                        if (state.running) R.string.stopwatch_lap else R.string.stopwatch_reset
                    )
                )
            }
            Button(
                onClick = viewModel::toggleStopwatch,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    stringResource(
                        if (state.running) R.string.stopwatch_pause else R.string.stopwatch_start
                    )
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(Modifier.fillMaxWidth()) {
            items(state.laps, key = { it.index }) { lap ->
                val highlight = when (lap.index) {
                    fastest -> MaterialTheme.colorScheme.tertiary
                    slowest -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.stopwatch_lap_number, lap.index),
                        color = highlight
                    )
                    Text(
                        text = DurationFormatter.formatPrecise(lap.splitMillis),
                        fontFamily = FontFamily.Monospace,
                        color = highlight
                    )
                    Text(
                        text = DurationFormatter.formatPrecise(lap.totalMillis),
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (state.laps.isEmpty()) {
            FilledTonalButton(onClick = {}, enabled = false) {
                Text(stringResource(R.string.stopwatch_no_laps))
            }
        }
    }
}
