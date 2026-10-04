package com.digitalclockpro.data.sound

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.digitalclockpro.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The click and the haptics of the chess clock.
 *
 * Uses [SoundPool], not `MediaPlayer`: the click has to land on the same frame as the tap, and
 * MediaPlayer's prepare/start path adds tens of milliseconds of latency — enough to feel broken
 * in a bullet game. SoundPool decodes the sample once into memory and replays it instantly.
 *
 * It is also deliberately **not** one of the three alarm players: this is a UI sound effect on
 * `USAGE_GAME`, so it respects the media/game volume and never ducks or hijacks an alarm.
 */
@Singleton
class ChessClockFeedback @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private var pool: SoundPool? = null
    private var clickId: Int = 0
    private var loaded = false

    /** Loading is async, so warm it up when the screen opens rather than on the first press. */
    fun prepare() {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder()
            // Two streams: a fast press can overlap the tail of the previous click.
            .setMaxStreams(2)
            .setAudioAttributes(attributes)
            .build()
            .also { sp ->
                sp.setOnLoadCompleteListener { _, _, status -> loaded = status == 0 }
                clickId = sp.load(context, R.raw.chess_click, 1)
            }
    }

    /** Fired when a player hands over the clock. Silently does nothing if still loading. */
    fun click(soundEnabled: Boolean, hapticsEnabled: Boolean) {
        if (soundEnabled && loaded) {
            pool?.play(clickId, 1f, 1f, 1, 0, 1f)
        }
        if (hapticsEnabled) {
            vibrate(PRESS_VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }

    /** A short double buzz as a clock enters its last seconds. */
    fun lowTimeWarning(hapticsEnabled: Boolean) {
        if (!hapticsEnabled) return
        runCatching {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 35, 60, 35), -1)
            vibrator()?.vibrate(effect)
        }
    }

    /** A long buzz when a flag falls — the one event that must be unmissable. */
    fun flagFall(hapticsEnabled: Boolean) {
        if (!hapticsEnabled) return
        vibrate(400L, VibrationEffect.DEFAULT_AMPLITUDE)
    }

    /** Frees the decoded sample. Must be called when the screen leaves composition. */
    fun release() {
        pool?.release()
        pool = null
        loaded = false
    }

    private fun vibrate(millis: Long, amplitude: Int) {
        runCatching {
            vibrator()?.vibrate(VibrationEffect.createOneShot(millis, amplitude))
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

    private companion object { const val PRESS_VIBRATION_MILLIS = 18L }
}
