package com.digitalclockpro.alarm

import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.digitalclockpro.domain.model.VibrationPattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VibrationController @Inject constructor(private val vibrator: Vibrator) {

    private val alarmAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun start(pattern: VibrationPattern) {
        if (pattern == VibrationPattern.NONE || !vibrator.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(pattern.timings, pattern.repeatIndex)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            vibrator.vibrate(effect, android.os.VibrationAttributes.createForUsage(
                android.os.VibrationAttributes.USAGE_ALARM
            ))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(effect, alarmAttributes)
        }
    }

    fun stop() = vibrator.cancel()
}
