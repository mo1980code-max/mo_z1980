package com.digitalclockpro.data.ringtone

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.digitalclockpro.R
import com.digitalclockpro.alarm.AlarmSessionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looping alert for a finished countdown timer.
 *
 * A third player, separate from both `AlarmSoundPlayer` (owned by `AlarmService`) and
 * [RingtonePreviewPlayer]: a timer must be able to ring while the user is elsewhere in the app,
 * and must never interrupt — or be interrupted by — a real alarm.
 *
 * It plays the tone bundled in the APK rather than the system ringtone, so it also works before
 * the first unlock and needs no storage access.
 */
@Singleton
class TimerAlertPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: AlarmSessionManager
) {

    private var player: MediaPlayer? = null

    val isPlaying: Boolean get() = player != null

    /** Starts the looping alert. A ringing alarm always wins: the timer stays silent. */
    fun start() {
        if (sessionManager.isRinging) {
            Log.i(TAG, "Alarm is ringing; timer alert suppressed to avoid two sounds at once")
            return
        }
        stop()
        runCatching {
            val attributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            player = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(
                    context,
                    Uri.parse("android.resource://${context.packageName}/${R.raw.fallback_alarm}")
                )
                isLooping = true
                prepare()
                start()
            }
            vibrate()
        }.onFailure {
            Log.w(TAG, "Could not start timer alert", it)
            stop()
        }
    }

    fun stop() {
        player?.let { mediaPlayer ->
            runCatching { if (mediaPlayer.isPlaying) mediaPlayer.stop() }
            runCatching { mediaPlayer.reset() }
            runCatching { mediaPlayer.release() }
        }
        player = null
        runCatching { vibrator()?.cancel() }
    }

    private fun vibrate() {
        val vibrator = vibrator() ?: return
        val pattern = longArrayOf(0, 400, 300, 400, 1000)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(pattern, 0)
            }
        }
    }

    private fun vibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    private companion object { const val TAG = "TimerAlertPlayer" }
}
