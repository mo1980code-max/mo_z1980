package com.digitalclockpro.presentation.ringing

import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import android.view.accessibility.AccessibilityManager
import android.content.Context
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.alarm.challenge.ShakeDetector
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.DismissChallenge
import com.digitalclockpro.presentation.common.rememberCurrentTime
import androidx.compose.ui.res.stringResource
import com.digitalclockpro.R
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun AlarmRingingScreen(
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    onFinished: () -> Unit,
    viewModel: AlarmRingingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberCurrentTime(withSeconds = false)

    LaunchedEffect(state.solved) { if (state.solved) onDismiss() }
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(48.dp))
            Text(
                text = TimeFormatters.formatTime(now, use24h = false, showSeconds = false),
                fontSize = 84.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE0E0E0)
            )
            Text(
                text = state.alarm?.label?.takeIf { it.isNotBlank() } ?: stringResource(R.string.alarm),
                fontSize = 20.sp,
                color = Color(0xFF9E9E9E)
            )
        }

        when (val challenge = state.alarm?.challenge) {
            is DismissChallenge.Math -> MathChallengeUi(state, viewModel::submitMathAnswer)
            is DismissChallenge.Shake -> ShakeChallengeUi(state, challenge.shakeCount, viewModel::onShake)
            is DismissChallenge.Sequence -> SequenceChallengeUi(state, viewModel::onSequenceTap)
            else -> SlideToDismiss(onDismiss = onDismiss)
        }

        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedButton(
                onClick = onSnooze,
                enabled = state.snoozesLeft > 0,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) {
                Text(
                    if (state.snoozesLeft > 0)
                        stringResource(
                        R.string.snooze_minutes_left,
                        state.alarm?.snoozeMinutes ?: 9,
                        state.snoozesLeft
                    )
                    else stringResource(R.string.no_snoozes_left),
                    fontSize = 18.sp
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MathChallengeUi(state: RingingUiState, onSubmit: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            stringResource(
                R.string.ringing_problem_index,
                state.problemIndex + 1,
                state.problems.size
            ),
            color = Color(0xFF9E9E9E)
        )
        Text(
            state.currentProblem?.question ?: "",
            fontSize = 44.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFE0E0E0)
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter { ch -> ch.isDigit() || ch == '-' } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = KeyboardType.NumberPassword
            ),
            singleLine = true,
            isError = state.error != null,
            supportingText = { state.error?.let { Text(it) } }
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = { onSubmit(input); input = "" }, enabled = input.isNotBlank()) {
            Text(stringResource(R.string.ringing_check))
        }
    }
}

@Composable
private fun ShakeChallengeUi(state: RingingUiState, required: Int, onShake: (Int) -> Unit) {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val detector = ShakeDetector(context) { count -> onShake(count) }
        detector.start()
        onDispose { detector.stop() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.shake_to_dismiss), fontSize = 24.sp, color = Color(0xFFE0E0E0))
        Text(stringResource(R.string.ringing_shake_progress, state.shakeProgress, required), fontSize = 40.sp, color = Color(0xFF00E5FF))
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { (state.shakeProgress.toFloat() / required).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(0.7f)
        )
    }
}

@Composable
private fun SequenceChallengeUi(state: RingingUiState, onTap: (Int) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.tap_in_order), color = Color(0xFF9E9E9E))
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.sequenceTiles) { value ->
                val done = value <= state.sequenceProgress
                Button(
                    onClick = { onTap(value) },
                    enabled = !done,
                    modifier = Modifier.size(72.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (done) Color(0xFF263238) else Color(0xFF37474F)
                    )
                ) { Text("$value", fontSize = 22.sp) }
            }
        }
    }
}

/**
 * Dismissing the alarm, by drag or by button.
 *
 * A horizontal drag is deliberate friction: it is hard to trigger with a sleeve or a cheek while
 * half asleep, which a plain button is not. That friction is also completely impassable for
 * anyone using TalkBack — touch exploration consumes the drag, so a screen-reader user had **no
 * way at all** to turn the alarm off. The gesture carried no semantics either, so the control
 * was not even announced.
 *
 * Two things fix that. When touch exploration is active the slider is replaced by a real button,
 * because asking a screen-reader user to perform a precision drag is not an accessible design.
 * And the slider itself now exposes a click action, so assistive tech that is not full
 * touch exploration can still activate it.
 */
@Composable
private fun SlideToDismiss(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val accessibilityManager = remember(context) {
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    }
    // Read live rather than once: TalkBack can be switched on while the alarm is ringing, which
    // is exactly when someone would reach for it.
    var touchExploration by remember { mutableStateOf(accessibilityManager.isTouchExplorationEnabled) }
    DisposableEffect(accessibilityManager) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { enabled ->
            touchExploration = enabled
        }
        accessibilityManager.addTouchExplorationStateChangeListener(listener)
        onDispose { accessibilityManager.removeTouchExplorationStateChangeListener(listener) }
    }

    val dismissLabel = stringResource(R.string.slide_to_dismiss_action)

    if (touchExploration) {
        Button(
            onClick = onDismiss,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00695C))
        ) {
            Text(stringResource(R.string.dismiss_button), fontSize = 20.sp)
        }
        return
    }

    var dragged by remember { mutableStateOf(0f) }
    // RTL: the arrow in the label points left, so the gesture must travel left too. Multiplying
    // the raw delta by the layout direction keeps a single progress value for both directions.
    val directionSign = if (LocalLayoutDirection.current == LayoutDirection.Rtl) -1f else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            // Announce the control and give it an activatable action. Without this the box is
            // an unlabelled, inert rectangle to every accessibility service.
            .semantics(mergeDescendants = true) {
                contentDescription = dismissLabel
                role = Role.Button
                onClick(label = dismissLabel) { onDismiss(); true }
            }
            .pointerInput(directionSign) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragged < size.width * 0.6f) dragged = 0f },
                    onHorizontalDrag = { _, delta ->
                        dragged = (dragged + delta * directionSign)
                            .coerceIn(0f, size.width.toFloat())
                        if (dragged > size.width * 0.75f) onDismiss()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            stringResource(R.string.slide_to_dismiss),
            fontSize = 20.sp,
            color = Color(0xFF80CBC4),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
