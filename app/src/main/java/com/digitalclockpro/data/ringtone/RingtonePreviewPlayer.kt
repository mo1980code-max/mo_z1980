package com.digitalclockpro.data.ringtone

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.Log
import com.digitalclockpro.alarm.AlarmSessionManager
import com.digitalclockpro.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Short audition of a ringtone inside the alarm editor.
 *
 * Intentionally **separate from `AlarmSoundPlayer`**, and built on [MediaPlayer] rather than
 * ExoPlayer, so the two can never share state: `AlarmSoundPlayer` is owned by `AlarmService` and
 * is mid-ramp while an alarm rings, and reusing it would stop or re-target a live alarm.
 *
 * Guarantees:
 *  - plays at most [PREVIEW_DURATION_MILLIS] and stops itself;
 *  - starting a new preview stops the previous one;
 *  - [stop] is idempotent and safe to call from `onCleared`/navigation;
 *  - **refuses to play while an alarm is actually ringing** ([AlarmSessionManager.isRinging]),
 *    returning [Result.AlarmRinging] so the UI can explain why.
 */
@Singleton
class RingtonePreviewPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: AlarmSessionManager,
    @ApplicationScope private val scope: CoroutineScope
) {

    /** Outcome of a [preview] request; everything is recoverable, nothing throws. */
    sealed interface Result {
        data object Playing : Result
        /** Nothing to audition (the "Silent" option). */
        data object Silent : Result
        /** An alarm is ringing right now — previewing would fight with it. */
        data object AlarmRinging : Result
        data class Failed(val uri: String?) : Result
    }

    private val audioManager: AudioManager? =
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var player: MediaPlayer? = null
    private var stopJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null

    private val _previewingUri = MutableStateFlow<String?>(null)

    /** Uri currently being auditioned, or null. Drives the play/stop icon in the picker. */
    val previewingUri: StateFlow<String?> = _previewingUri.asStateFlow()

    /**
     * Starts (or restarts) a preview of [uriString].
     *
     * Passing the Uri that is already playing acts as a toggle and stops it.
     */
    fun preview(uriString: String?): Result {
        // Never compete with a real alarm.
        if (sessionManager.isRinging) {
            stop()
            return Result.AlarmRinging
        }
        if (uriString.isNullOrBlank() || uriString == AlarmSound.SILENT_URI) {
            stop()
            return Result.Silent
        }
        // Tapping the playing item again = stop.
        if (_previewingUri.value == uriString) {
            stop()
            return Result.Silent
        }

        stop()

        return runCatching {
            val attributes = AudioAttributes.Builder()
                // Alarm usage so the user auditions it at the volume it will actually ring at.
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            requestTransientFocus(attributes)

            val mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(context, Uri.parse(uriString))
                isLooping = false
                setOnCompletionListener { stop() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Preview error what=$what extra=$extra")
                    stop()
                    true
                }
                prepare()      // local file / content Uri: cheap and synchronous
                start()
            }
            player = mediaPlayer
            _previewingUri.value = uriString

            stopJob = scope.launch {
                delay(PREVIEW_DURATION_MILLIS)
                stop()
            }
            Result.Playing as Result
        }.getOrElse { error ->
            Log.w(TAG, "Unable to preview $uriString", error)
            stop()
            Result.Failed(uriString)
        }
    }

    /** Stops any preview and releases the player. Safe to call repeatedly. */
    fun stop() {
        stopJob?.cancel()
        stopJob = null
        player?.let { mediaPlayer ->
            runCatching { if (mediaPlayer.isPlaying) mediaPlayer.stop() }
            runCatching { mediaPlayer.reset() }
            runCatching { mediaPlayer.release() }
        }
        player = null
        abandonFocus()
        _previewingUri.value = null
    }

    private fun requestTransientFocus(attributes: AudioAttributes) {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val request = AudioFocusRequest
                    .Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener { change ->
                        if (change == AudioManager.AUDIOFOCUS_LOSS ||
                            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
                        ) {
                            stop()
                        }
                    }
                    .build()
                focusRequest = request
                manager.requestAudioFocus(request)
            } else {
                @Suppress("DEPRECATION")
                manager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_ALARM,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
            }
        }
    }

    private fun abandonFocus() {
        val manager = audioManager ?: return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                focusRequest?.let { manager.abandonAudioFocusRequest(it) }
            } else {
                @Suppress("DEPRECATION")
                manager.abandonAudioFocus(null)
            }
        }
        focusRequest = null
    }

    companion object {
        private const val TAG = "RingtonePreview"

        /** Long enough to recognise a tone, short enough not to annoy. */
        const val PREVIEW_DURATION_MILLIS = 8_000L
    }
}
