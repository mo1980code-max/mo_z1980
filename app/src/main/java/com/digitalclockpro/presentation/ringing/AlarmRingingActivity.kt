package com.digitalclockpro.presentation.ringing

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowCompat
import com.digitalclockpro.alarm.AlarmService
import com.digitalclockpro.core.ui.theme.DigitalClockProTheme
import com.digitalclockpro.domain.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * Full-screen alarm UI. Launched either by the full-screen intent (locked device) or directly by
 * [AlarmService]. Shows over the lock screen and turns the display on.
 */
@AndroidEntryPoint
class AlarmRingingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // Always dark + high contrast: never blind a half-asleep user.
            DigitalClockProTheme(themeMode = ThemeMode.DARK, dynamicColor = false, amoledBlack = true) {
                AlarmRingingScreen(
                    // The session was cleared by the service (dismissed from the notification,
                    // auto-silenced, or handled on another device surface) -> just close.
                    onFinished = { finishAndRemoveTask() },
                    onSnooze = {
                        AlarmService.command(this, AlarmService.ACTION_SNOOZE)
                        finishAndRemoveTask()
                    },
                    onDismiss = {
                        AlarmService.command(this, AlarmService.ACTION_DISMISS)
                        finishAndRemoveTask()
                    }
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            (getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager)
                .requestDismissKeyguard(this, null)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** The back gesture must not silence the alarm. */
    override fun onBackPressed() = Unit

    companion object {
        const val EXTRA_ALARM_ID = "alarmId"

        fun intent(context: Context, alarmId: Long): Intent =
            Intent(context, AlarmRingingActivity::class.java)
                .putExtra(EXTRA_ALARM_ID, alarmId)
                .addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_NO_USER_ACTION
                )
    }
}
