package com.digitalclockpro.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.usecase.DismissAlarmUseCase
import com.digitalclockpro.domain.usecase.SnoozeAlarmUseCase
import com.digitalclockpro.presentation.ringing.AlarmRingingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * Foreground service that owns the ringing session: audio (with ramp), vibration, the
 * full-screen notification, the wake-lock and the auto-silence timeout.
 *
 * Lives independently from [AlarmRingingActivity] so swiping the activity away, rotating, or the
 * system killing the task never stops the alarm.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var notifications: AlarmNotifications
    @Inject lateinit var soundPlayer: AlarmSoundPlayer
    @Inject lateinit var vibrationController: VibrationController
    @Inject lateinit var snoozeAlarm: SnoozeAlarmUseCase
    @Inject lateinit var dismissAlarm: DismissAlarmUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null
    private var autoSilenceJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(intent.getLongExtra(AppIntents.EXTRA_ALARM_ID, -1L))
            ACTION_SNOOZE -> scope.launch {
                val id = ringingAlarm.value?.id ?: return@launch
                snoozeAlarm(id); stopRinging()
            }
            ACTION_DISMISS -> scope.launch {
                val id = ringingAlarm.value?.id ?: return@launch
                dismissAlarm(id); stopRinging()
            }
            ACTION_STOP -> stopRinging()
            else -> stopRinging()
        }
        return START_STICKY
    }

    private fun start(alarmId: Long) {
        if (alarmId <= 0L) { stopSelf(); return }
        acquireWakeLock()
        scope.launch {
            val alarm = alarmRepository.getAlarm(alarmId) ?: run { stopRinging(); return@launch }
            _ringingAlarm.value = alarm

            val timeText = TimeFormatters.formatTime(LocalDateTime.now(), use24h = false, showSeconds = false)
            val notification = notifications.buildRingingNotification(alarm, timeText)
            ServiceCompat.startForeground(
                this@AlarmService,
                notifications.ringingNotificationId(alarmId),
                notification,
                when {
                    // API 34+ requires every declared type to be justified; we declare both.
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    else -> 0
                }
            )

            soundPlayer.play(
                scope = scope,
                soundUri = alarm.soundUri?.let(Uri::parse) ?: defaultAlarmUri(),
                targetVolumePercent = alarm.volumePercent,
                rampSeconds = alarm.volumeRampSeconds
            )
            vibrationController.start(alarm.vibrationPattern)

            // Launch the full-screen UI directly when the device is unlocked/interactive and the
            // OEM suppressed the full-screen intent.
            runCatching { startActivity(AlarmRingingActivity.intent(this@AlarmService, alarmId)) }

            autoSilenceJob = scope.launch {
                delay(AUTO_SILENCE_MILLIS)
                Log.i(TAG, "Auto-silencing alarm $alarmId after 15 min")
                snoozeAlarm(alarmId)
                stopRinging()
            }
        }
    }

    private fun stopRinging() {
        autoSilenceJob?.cancel()
        soundPlayer.stop()
        vibrationController.stop()
        _ringingAlarm.value = null
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire(AUTO_SILENCE_MILLIS + 60_000L)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    private fun defaultAlarmUri(): Uri =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)

    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.stop()
        vibrationController.stop()
        releaseWakeLock()
        scope.cancel()
    }

    companion object {
        const val ACTION_START = "com.digitalclockpro.service.START"
        const val ACTION_STOP = "com.digitalclockpro.service.STOP"
        const val ACTION_SNOOZE = "com.digitalclockpro.service.SNOOZE"
        const val ACTION_DISMISS = "com.digitalclockpro.service.DISMISS"

        private const val TAG = "AlarmService"
        private const val WAKE_LOCK_TAG = "DigitalClockPro:AlarmService"
        private const val AUTO_SILENCE_MILLIS = 15 * 60 * 1000L

        /** Observed by [AlarmRingingActivity] so the UI always mirrors the service state. */
        private val _ringingAlarm = MutableStateFlow<Alarm?>(null)
        val ringingAlarm: StateFlow<Alarm?> = _ringingAlarm.asStateFlow()

        fun command(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
