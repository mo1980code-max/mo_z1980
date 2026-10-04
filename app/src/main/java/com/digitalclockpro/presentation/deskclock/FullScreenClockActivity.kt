package com.digitalclockpro.presentation.deskclock

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.digitalclockpro.R
import com.digitalclockpro.ads.AdsController
import com.digitalclockpro.clockengine.AdSurface
import com.digitalclockpro.clockengine.ClockEngine
import com.digitalclockpro.core.ui.theme.DigitalClockProTheme
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.AnalogFace
import com.digitalclockpro.domain.model.HandMotion
import com.digitalclockpro.domain.model.ThemeMode
import com.digitalclockpro.presentation.common.AnalogClock
import com.digitalclockpro.presentation.common.rememberCurrentTime
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Desk Clock: a full-screen, always-on clock for a bedside dock.
 *
 * Three things make this safe to leave running for hours:
 *  1. **Immersive** — system bars are hidden and only come back on a swipe.
 *  2. **Burn-in protection** — the whole clock is translated along a slow Lissajous path
 *     ([ClockEngine.burnInOffset]) so no OLED pixel is ever lit continuously.
 *  3. **Brightness control** — a vertical drag sets the *window* brightness only, which needs no
 *     permission and is reverted automatically when the screen closes (unlike
 *     `Settings.System.SCREEN_BRIGHTNESS`, which requires WRITE_SETTINGS).
 *
 * `FLAG_KEEP_SCREEN_ON` is a window flag, not a wake lock: the screen stays on only while this
 * activity is in the foreground and is released by the framework the moment it is not.
 */
@AndroidEntryPoint
class FullScreenClockActivity : ComponentActivity() {

    @Inject lateinit var adsController: AdsController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Ad-free surface forever (AdPolicy.DESK_CLOCK): no banner here, and the app-open
        // ad may never cover a bedside clock — including on the foreground return to it.
        adsController.setSurface(this, AdSurface.DESK_CLOCK)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            // Always dark + AMOLED black: this screen exists to be looked at in a dark room.
            DigitalClockProTheme(
                themeMode = ThemeMode.DARK,
                dynamicColor = false,
                amoledBlack = true
            ) {
                DeskClockScreen(
                    onBrightnessChange = { value ->
                        window.attributes = window.attributes.apply { screenBrightness = value }
                    },
                    onExit = { finish() }
                )
            }
        }
    }

    override fun onDestroy() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        super.onDestroy()
    }

    companion object {
        fun intent(context: Context) = Intent(context, FullScreenClockActivity::class.java)
    }
}

@Composable
private fun DeskClockScreen(
    onBrightnessChange: (Float) -> Unit,
    onExit: () -> Unit,
    viewModel: DeskClockViewModel = hiltViewModel()
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val density = LocalDensity.current

    var analog by remember { mutableStateOf(false) }
    var brightness by remember { mutableFloatStateOf(DEFAULT_BRIGHTNESS) }
    var showBrightnessHint by remember { mutableStateOf(false) }
    var battery by remember { mutableIntStateOf(batteryPercent(context) ?: 0) }

    val startedAt = remember { SystemClock.elapsedRealtime() }
    var elapsed by remember { mutableFloatStateOf(0f) }

    val now by rememberCurrentTime(withSeconds = true)

    LaunchedEffect(Unit) { onBrightnessChange(brightness) }

    // Burn-in drift + battery refresh, recomputed once a minute: cheap, and the drift is meant
    // to be imperceptible anyway.
    LaunchedEffect(Unit) {
        while (true) {
            elapsed = (SystemClock.elapsedRealtime() - startedAt).toFloat()
            battery = batteryPercent(context) ?: battery
            delay(BURN_IN_STEP_MILLIS)
        }
    }

    // Hide the brightness readout shortly after the drag ends.
    LaunchedEffect(brightness) {
        if (showBrightnessHint) {
            delay(1_200)
            showBrightnessHint = false
        }
    }

    val amplitudePx = with(density) { BURN_IN_AMPLITUDE_DP.dp.toPx() }
    val offset = ClockEngine.burnInOffset(
        elapsedMillis = elapsed.toLong(),
        amplitudeX = amplitudePx,
        amplitudeY = amplitudePx * 0.6f
    )
    val offsetX by animateFloatAsState(offset.x, tween(2_000), label = "burnInX")
    val offsetY by animateFloatAsState(offset.y, tween(2_000), label = "burnInY")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    brightness = ClockEngine.brightnessAfterDrag(
                        current = brightness,
                        dragDeltaPx = dragAmount,
                        screenHeightPx = size.height.toFloat()
                    )
                    showBrightnessHint = true
                    onBrightnessChange(brightness)
                }
            }
            // Single tap anywhere flips analog <-> digital; no chrome to get in the way.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { analog = !analog },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.offset { IntOffset(offsetX.toInt(), offsetY.toInt()) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (analog) {
                AnalogClock(
                    face = AnalogFace.NEON_ANALOG,
                    modifier = Modifier.size(DESK_DIAL_SIZE_DP.dp),
                    motion = HandMotion.SMOOTH,
                    showSeconds = true,
                    showNumerals = true
                )
            } else {
                Text(
                    // Obeys the app-wide 12/24h setting. Seconds stay on unconditionally: a desk
                    // clock you stare at from across the room is exactly where a ticking
                    // seconds field earns its place, whatever the in-app preference says.
                    text = TimeFormatters.formatTime(
                        now,
                        use24h = preferences.use24Hour,
                        showSeconds = true
                    ),
                    fontSize = 86.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF00E5FF)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = TimeFormatters.formatDate(now, stringResource(R.string.desk_date_pattern)),
                fontSize = 20.sp,
                color = Color(0xFF9E9E9E)
            )

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = stringResource(R.string.widget_battery_percent, battery),
                    fontSize = 16.sp,
                    color = Color(0xFF9E9E9E)
                )
                Text(
                    text = stringResource(
                        if (analog) R.string.desk_tap_for_digital else R.string.desk_tap_for_analog
                    ),
                    fontSize = 16.sp,
                    color = Color(0xFF616161)
                )
            }
        }

        if (showBrightnessHint) {
            Text(
                text = stringResource(
                    R.string.desk_brightness,
                    ClockEngine.brightnessPercent(brightness)
                ),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                color = Color(0xFFBDBDBD),
                fontSize = 18.sp
            )
        }

        Text(
            text = stringResource(R.string.desk_exit),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .clickable(onClick = onExit)
                .padding(12.dp),
            color = Color(0xFF616161),
            fontSize = 14.sp
        )
    }

    // Restore the system brightness when the desk clock closes.
    DisposableEffect(Unit) {
        onDispose { onBrightnessChange(WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE) }
    }
}

private fun batteryPercent(context: Context): Int? {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        ?: return null
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level < 0 || scale <= 0) null else level * 100 / scale
}

private const val DEFAULT_BRIGHTNESS = 0.45f
private const val BURN_IN_AMPLITUDE_DP = 20
private const val BURN_IN_STEP_MILLIS = 10_000L
private const val DESK_DIAL_SIZE_DP = 300
