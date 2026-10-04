package com.digitalclockpro.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Plays the alarm sound on STREAM_ALARM with a configurable **volume ramp**
 * (0 % -> target over 10–60 s) using a perceptual (quadratic) curve.
 */
@Singleton
class AlarmSoundPlayer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var player: ExoPlayer? = null
    private var rampJob: Job? = null

    fun play(
        scope: CoroutineScope,
        soundUri: Uri,
        targetVolumePercent: Int,
        rampSeconds: Int
    ) {
        stop()
        val exo = ExoPlayer.Builder(context).build().apply {
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_ALARM)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_SONIFICATION)
                    .build(),
                /* handleAudioFocus = */ false
            )
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItem(MediaItem.fromUri(soundUri))
            volume = if (rampSeconds > 0) 0f else targetVolumePercent / 100f
            prepare()
            playWhenReady = true
        }
        player = exo
        ensureAlarmStreamAudible()

        if (rampSeconds > 0) {
            rampJob = scope.launch {
                val target = targetVolumePercent / 100f
                val steps = (rampSeconds * 4).coerceAtLeast(1)   // 250 ms granularity
                for (step in 1..steps) {
                    val linear = step.toFloat() / steps
                    exo.volume = target * linear.pow(2f)         // ease-in, gentler start
                    delay(250)
                }
                exo.volume = target
            }
        }
    }

    /** The system alarm stream can be at 0; nudge it to at least 30 % so the alarm is audible. */
    private fun ensureAlarmStreamAudible() = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        if (am.getStreamVolume(AudioManager.STREAM_ALARM) < max * 0.3f) {
            am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 0.6f).toInt(), 0)
        }
    }.onFailure { Log.w(TAG, "Unable to adjust alarm stream", it) }

    fun stop() {
        rampJob?.cancel(); rampJob = null
        player?.run { stop(); release() }
        player = null
    }

    @Suppress("unused")
    private val legacyAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    private companion object { const val TAG = "AlarmSoundPlayer" }
}
