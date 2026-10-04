package com.digitalclockpro.presentation.chess

import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.R
import com.digitalclockpro.clockengine.ChessClockEngine
import com.digitalclockpro.clockengine.ChessClockEngine.Phase
import com.digitalclockpro.clockengine.ChessClockEngine.Player
import com.digitalclockpro.clockengine.ChessClockEngine.TimeControlType

/**
 * A two-sided tournament clock.
 *
 * The board is split in half and the far half is rotated 180°, so two people sitting across a
 * table both read their own clock upright. Compose applies the layer transform to hit testing
 * as well as to drawing, so the rotated half stays tappable where it looks tappable.
 */
@Composable
fun ChessClockScreen(viewModel: ChessClockViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val prefs by viewModel.prefs.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }

    // A chess clock is useless if the screen sleeps mid-game. This is a window flag, not a
    // wake lock: the system drops it the moment the screen leaves the foreground.
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    Column(Modifier.fillMaxSize()) {
        // ---- far side, rotated to face the opponent ----
        PlayerPanel(
            side = Player.B,
            state = state,
            now = now,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .graphicsLayer { rotationZ = 180f },
            onTap = { viewModel.onTap(Player.B) }
        )

        ControlBar(
            state = state,
            soundEnabled = prefs.soundEnabled,
            onPauseResume = viewModel::pauseOrResume,
            onReset = viewModel::reset,
            onToggleSound = viewModel::toggleSound,
            onOpenSettings = { showSettings = true }
        )

        // ---- near side ----
        PlayerPanel(
            side = Player.A,
            state = state,
            now = now,
            modifier = Modifier.fillMaxWidth().weight(1f),
            onTap = { viewModel.onTap(Player.A) }
        )
    }

    if (showSettings) {
        TimeControlDialog(
            current = state.control,
            onPreset = { viewModel.applyPreset(it); showSettings = false },
            onCustom = { m, inc, type -> viewModel.applyCustom(m, inc, type); showSettings = false },
            onDismiss = { showSettings = false }
        )
    }
}

// ---------------------------------------------------------------------- player half

@Composable
private fun PlayerPanel(
    side: Player,
    state: ChessClockEngine.State,
    now: Long,
    modifier: Modifier,
    onTap: () -> Unit
) {
    val remaining = ChessClockEngine.remaining(state, side, now)
    val isActive = state.active == side && state.phase == Phase.RUNNING
    val hasFlagged = state.flagged == side
    val critical = ChessClockEngine.isCritical(state, side, now) && state.phase != Phase.IDLE

    // Only the running clock blinks. A paused board that pulses red looks like a malfunction.
    val blink = if (critical && isActive) {
        val transition = rememberInfiniteTransition(label = "lowTime")
        transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(450), RepeatMode.Reverse),
            label = "lowTimeAlpha"
        ).value
    } else {
        1f
    }

    val target = when {
        hasFlagged -> Color(0xFF7F0000)
        critical && isActive -> Color(0xFF8E1111)
        isActive -> MaterialTheme.colorScheme.primaryContainer
        // Idle side is dimmed so the board reads at a glance from across the table.
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val background by animateColorAsState(target, tween(220), label = "panelColor")
    val onBackground = if (hasFlagged || (critical && isActive)) Color.White
    else MaterialTheme.colorScheme.onSurface

    val sideLabel = stringResource(
        if (side == Player.A) R.string.chess_player_a else R.string.chess_player_b
    )
    val stateLabel = when {
        hasFlagged -> stringResource(R.string.chess_flagged)
        state.phase == Phase.IDLE -> stringResource(R.string.chess_tap_to_start)
        isActive -> stringResource(R.string.chess_your_move)
        else -> stringResource(R.string.chess_waiting)
    }

    Box(
        modifier = modifier
            .background(background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = state.phase == Phase.IDLE || state.phase == Phase.RUNNING,
                onClick = onTap
            )
            // One spoken description for the whole half: TalkBack users get "Player A, 2 minutes
            // 30 seconds, your move" instead of three disconnected fragments.
            .semantics {
                contentDescription = "$sideLabel, ${spokenTime(remaining)}, $stateLabel"
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = sideLabel,
                style = MaterialTheme.typography.labelLarge,
                color = onBackground.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatClock(remaining),
                fontSize = 64.sp,
                fontFamily = FontFamily.Monospace,
                color = onBackground.copy(alpha = blink),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.chess_moves, state.player(side).moves),
                style = MaterialTheme.typography.labelMedium,
                color = onBackground.copy(alpha = 0.7f)
            )
            Text(
                text = stateLabel,
                style = MaterialTheme.typography.labelSmall,
                color = onBackground.copy(alpha = 0.55f)
            )
        }
    }
}

// ---------------------------------------------------------------------- controls

@Composable
private fun ControlBar(
    state: ChessClockEngine.State,
    soundEnabled: Boolean,
    onPauseResume: () -> Unit,
    onReset: () -> Unit,
    onToggleSound: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        IconButton(
            onClick = onPauseResume,
            enabled = state.phase == Phase.RUNNING || state.phase == Phase.PAUSED
        ) {
            Icon(
                imageVector = if (state.phase == Phase.RUNNING) Icons.Filled.Pause
                else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (state.phase == Phase.RUNNING) R.string.chess_pause else R.string.chess_resume
                )
            )
        }

        IconButton(onClick = onReset) {
            Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.chess_reset))
        }

        Text(
            text = ChessClockEngine.shorthand(state.control),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace
        )

        IconButton(onClick = onToggleSound) {
            Icon(
                imageVector = if (soundEnabled) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                contentDescription = stringResource(
                    if (soundEnabled) R.string.chess_sound_on else R.string.chess_sound_off
                )
            )
        }

        IconButton(onClick = onOpenSettings) {
            Icon(
                Icons.Filled.Settings,
                contentDescription = stringResource(R.string.chess_time_control)
            )
        }
    }
}

@Composable
private fun TimeControlDialog(
    current: ChessClockEngine.TimeControl,
    onPreset: (ChessClockEngine.Preset) -> Unit,
    onCustom: (Int, Int, TimeControlType) -> Unit,
    onDismiss: () -> Unit
) {
    var minutes by remember { mutableStateOf(current.safeBase / 60_000L) }
    var increment by remember { mutableStateOf(current.safeBonus / 1_000L) }
    var type by remember { mutableStateOf(current.type) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chess_time_control)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.chess_presets),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChessClockEngine.Preset.entries.forEach { preset ->
                        FilterChip(
                            selected = preset.control == current,
                            onClick = { onPreset(preset) },
                            label = {
                                Text(
                                    stringResource(presetLabel(preset)) + "  " +
                                        ChessClockEngine.shorthand(preset.control)
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.chess_custom),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(6.dp))

                Stepper(
                    label = stringResource(R.string.chess_base_minutes, minutes),
                    onDecrease = { minutes = (minutes - 1).coerceAtLeast(1L) },
                    onIncrease = { minutes = (minutes + 1).coerceAtMost(360L) }
                )
                Stepper(
                    label = stringResource(R.string.chess_bonus_seconds, increment),
                    onDecrease = { increment = (increment - 1).coerceAtLeast(0L) },
                    onIncrease = { increment = (increment + 1).coerceAtMost(300L) }
                )

                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.chess_mode),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(6.dp))
                Column {
                    TimeControlType.entries.forEach { option ->
                        FilterChip(
                            selected = type == option,
                            onClick = { type = option },
                            label = { Text(stringResource(typeLabel(option))) },
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = stringResource(typeExplanation(type)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onCustom(minutes.toInt(), increment.toInt(), type) }) {
                Text(stringResource(R.string.chess_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun Stepper(label: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row {
            TextButton(onClick = onDecrease) { Text("\u2212") }
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onIncrease) { Text("+") }
        }
    }
}

// ---------------------------------------------------------------------- formatting

/**
 * `M:SS` normally, `M:SS.t` under ten seconds.
 *
 * Rounding **up** matters: a clock that reads 0 while you still have 400 ms would look like a
 * bug, and worse, like you lost when you had not.
 */
private fun formatClock(millis: Long): String {
    val safe = millis.coerceAtLeast(0L)
    return if (safe < ChessClockEngine.CRITICAL_THRESHOLD_MILLIS) {
        val seconds = safe / 1000
        val tenths = (safe % 1000) / 100
        "%d.%d".format(seconds, tenths)
    } else {
        val total = (safe + 999) / 1000
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        val seconds = total % 60
        if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%d:%02d".format(minutes, seconds)
    }
}

/** Digits are unreadable to TalkBack as a clock; speak whole units instead. */
private fun spokenTime(millis: Long): String {
    val total = (millis.coerceAtLeast(0L) + 999) / 1000
    val minutes = total / 60
    val seconds = total % 60
    return "$minutes:%02d".format(seconds)
}

@StringRes
private fun presetLabel(preset: ChessClockEngine.Preset): Int = when (preset) {
    ChessClockEngine.Preset.BULLET_1_0,
    ChessClockEngine.Preset.BULLET_2_1 -> R.string.chess_category_bullet
    ChessClockEngine.Preset.BLITZ_3_2,
    ChessClockEngine.Preset.BLITZ_5_0 -> R.string.chess_category_blitz
    ChessClockEngine.Preset.RAPID_10_0,
    ChessClockEngine.Preset.RAPID_15_10 -> R.string.chess_category_rapid
    ChessClockEngine.Preset.CLASSICAL_30_30 -> R.string.chess_category_classical
}

@StringRes
private fun typeLabel(type: TimeControlType): Int = when (type) {
    TimeControlType.SUDDEN_DEATH -> R.string.chess_type_sudden_death
    TimeControlType.FISCHER_INCREMENT -> R.string.chess_type_fischer
    TimeControlType.SIMPLE_DELAY -> R.string.chess_type_simple_delay
    TimeControlType.BRONSTEIN -> R.string.chess_type_bronstein
}

@StringRes
private fun typeExplanation(type: TimeControlType): Int = when (type) {
    TimeControlType.SUDDEN_DEATH -> R.string.chess_explain_sudden_death
    TimeControlType.FISCHER_INCREMENT -> R.string.chess_explain_fischer
    TimeControlType.SIMPLE_DELAY -> R.string.chess_explain_simple_delay
    TimeControlType.BRONSTEIN -> R.string.chess_explain_bronstein
}
