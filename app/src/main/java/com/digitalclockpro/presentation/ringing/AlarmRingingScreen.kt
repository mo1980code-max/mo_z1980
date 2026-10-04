package com.digitalclockpro.presentation.ringing

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

@Composable
fun AlarmRingingScreen(
    onSnooze: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: AlarmRingingViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val now by rememberCurrentTime(withSeconds = false)

    LaunchedEffect(state.solved) { if (state.solved) onDismiss() }

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
                text = state.alarm?.label?.takeIf { it.isNotBlank() } ?: "Alarm",
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
                        "Snooze ${state.alarm?.snoozeMinutes ?: 9} min (${state.snoozesLeft} left)"
                    else "No snoozes left",
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
            "Problem ${state.problemIndex + 1} of ${state.problems.size}",
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
            Text("Check")
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
        Text("Shake your phone", fontSize = 24.sp, color = Color(0xFFE0E0E0))
        Text("${state.shakeProgress} / $required", fontSize = 40.sp, color = Color(0xFF00E5FF))
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
        Text("Tap the numbers in order", color = Color(0xFF9E9E9E))
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

@Composable
private fun SlideToDismiss(onDismiss: () -> Unit) {
    var dragged by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { if (dragged < size.width * 0.6f) dragged = 0f },
                    onHorizontalDrag = { _, delta ->
                        dragged = (dragged + delta).coerceIn(0f, size.width.toFloat())
                        if (dragged > size.width * 0.75f) onDismiss()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Slide to dismiss  →",
            fontSize = 20.sp,
            color = Color(0xFF80CBC4),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
