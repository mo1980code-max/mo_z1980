package com.digitalclockpro.presentation.chess

import android.app.Activity
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
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay

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
    val summaryUnlocked by viewModel.summaryUnlocked.collectAsStateWithLifecycle()
    val adUnavailable by viewModel.adUnavailableNotice.collectAsStateWithLifecycle()

    var showSettings by remember { mutableStateOf(false) }
    var confirmEndGame by remember { mutableStateOf(false) }

    // The rewarded ad needs a host activity; the chess tab is always composed inside one.
    val activity = LocalContext.current as? Activity

    // ---- game-over flow -------------------------------------------------------
    // The result dialog appears only 1.5 s AFTER the game has frozen: a flag fall is a
    // physical moment two people are still tapping through, and a dialog popping over the
    // last press is exactly how accidental taps happen. Both flags survive rotation (the
    // user's dismissal must not be un-done by turning the screen), and are re-armed the
    // moment a new game starts (the phase leaves FINISHED).
    var showResult by rememberSaveable { mutableStateOf(false) }
    var resultDismissed by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        when (state.phase) {
            Phase.FINISHED -> if (!resultDismissed) {
                delay(GAME_OVER_DELAY_MILLIS)
                showResult = true
            }
            else -> {
                showResult = false
                resultDismissed = false
            }
        }
    }

    // The gentle "no ad right now" notice is transient: it clears itself after a few seconds
    // so the dialog never paints itself into a corner while offline.
    if (adUnavailable) {
        LaunchedEffect(Unit) {
            delay(AD_NOTICE_MILLIS)
            viewModel.clearAdUnavailableNotice()
        }
    }

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
            onOpenSettings = { showSettings = true },
            onEndGame = { confirmEndGame = true }
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

    if (confirmEndGame) {
        EndGameConfirmDialog(
            onConfirm = {
                confirmEndGame = false
                viewModel.endGame()
            },
            onDismiss = { confirmEndGame = false }
        )
    }

    if (showResult) {
        GameOverDialog(
            state = state,
            summaryUnlocked = summaryUnlocked,
            adUnavailable = adUnavailable,
            onWatchAd = { activity?.let(viewModel::requestMatchSummary) },
            onRematch = viewModel::reset,
            onClose = {
                showResult = false
                resultDismissed = true
            }
        )
    }
}

// ---------------------------------------------------------------------- game over

/**
 * The end-of-game dialog: the result first, then the **opt-in** rewarded offer — and, once the
 * reward has actually been earned through `onUserEarnedReward`, the match summary it paid for.
 * No ad is ever started from here without an explicit tap, and nothing about the offer blocks
 * the result or the rematch. Whether an ad may exist at all is
 * [com.digitalclockpro.clockengine.AdPolicy]'s decision, enforced inside
 * [com.digitalclockpro.ads.RewardedAdManager]; this composable only renders what survived it.
 */
@Composable
private fun GameOverDialog(
    state: ChessClockEngine.State,
    summaryUnlocked: Boolean,
    adUnavailable: Boolean,
    onWatchAd: () -> Unit,
    onRematch: () -> Unit,
    onClose: () -> Unit
) {
    val summary = ChessClockEngine.summarize(state)

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.chess_game_over)) },
        text = {
            Column {
                if (summary == null) {
                    // Unreachable while the dialog is gated on FINISHED — but a summary-less
                    // game over still deserves a result line, never a crash.
                    Text(stringResource(R.string.chess_flagged))
                } else {
                    val winnerName = stringResource(
                        if (summary.winner == Player.A) R.string.chess_player_a
                        else R.string.chess_player_b
                    )
                    Text(
                        text = stringResource(
                            if (summary.endedBy == ChessClockEngine.EndReason.RESIGN)
                                R.string.chess_win_by_resignation
                            else R.string.chess_win_on_time,
                            winnerName
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )

                    if (!summaryUnlocked) {
                        // ---- the offer: a clear trade, never an interruption ----
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.chess_watch_ad_for_summary),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (adUnavailable) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.chess_ad_not_ready),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        // ---- the earned reward ----
                        Spacer(Modifier.height(12.dp))
                        SummaryCard(summary)
                    }
                }
            }
        },
        confirmButton = {
            if (summaryUnlocked || summary == null) {
                TextButton(onClick = onRematch) {
                    Text(stringResource(R.string.chess_reset))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // The way out stays one tap away whether or not the user watches anything.
                    TextButton(onClick = onRematch) {
                        Text(stringResource(R.string.chess_reset))
                    }
                    Button(onClick = onWatchAd) {
                        Icon(
                            Icons.Filled.EmojiEvents,
                            contentDescription = null,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.chess_watch_ad_cta))
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.chess_close)) }
        }
    )
}

/**
 * The reward: a compact two-column tally of the finished game. Numbers come straight from the
 * pure [ChessClockEngine.summarize]; every label is translated like everywhere else.
 */
@Composable
private fun SummaryCard(summary: ChessClockEngine.GameSummary) {
    Column {
        Text(
            text = stringResource(R.string.chess_summary_title),
            style = MaterialTheme.typography.titleSmall
        )
        Spacer(Modifier.height(8.dp))

        SummaryRow(
            label = "",
            a = stringResource(R.string.chess_player_a),
            b = stringResource(R.string.chess_player_b)
        )
        SummaryRow(
            label = stringResource(R.string.chess_summary_moves),
            a = summary.a.moves.toString(),
            b = summary.b.moves.toString()
        )
        SummaryRow(
            label = stringResource(R.string.chess_summary_avg_move),
            a = formatSeconds(summary.a.averageMillisPerMove),
            b = formatSeconds(summary.b.averageMillisPerMove)
        )
        SummaryRow(
            label = stringResource(R.string.chess_summary_time_left),
            a = formatClock(summary.a.remainingMillis),
            b = formatClock(summary.b.remainingMillis)
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.chess_summary_duration) +
                ": " + formatClock(summary.activeMillis),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SummaryRow(label: String, a: String, b: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1.4f)
        )
        Text(
            text = a,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = b,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun EndGameConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chess_end_game)) },
        text = { Text(stringResource(R.string.chess_end_game_confirm)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.chess_end_game)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
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
    onOpenSettings: () -> Unit,
    onEndGame: () -> Unit
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

        // Early end (resignation-style): confirmation-gated because it destroys a running
        // game, and disabled unless one actually is.
        IconButton(
            onClick = onEndGame,
            enabled = state.phase == Phase.RUNNING || state.phase == Phase.PAUSED
        ) {
            Icon(Icons.Filled.Flag, contentDescription = stringResource(R.string.chess_end_game))
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

/**
 * One decimal of seconds for the summary's per-move average — "3.5", locale digits, no unit
 * (the row label already says what it is). Null (no completed moves) reads as a dash.
 */
private fun formatSeconds(millis: Long?): String =
    millis?.let { "%.1f".format(it / 1000.0) } ?: "\u2014"

/**
 * The pause between "the game froze" and "the result dialog appears". Long enough that the
 * last, frantic tap of a flag fall cannot land on the dialog instead of the board; short
 * enough that the result still feels instant.
 */
private const val GAME_OVER_DELAY_MILLIS = 1_500L

/** How long the friendly "no ad available" notice stays up before clearing itself. */
private const val AD_NOTICE_MILLIS = 4_000L

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
