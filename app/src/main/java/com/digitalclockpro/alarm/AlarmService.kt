package com.digitalclockpro.alarm

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.core.util.TimeFormatters
import com.digitalclockpro.data.ringtone.RingtoneRepository
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.usecase.DismissAlarmUseCase
import com.digitalclockpro.domain.usecase.SnoozeAlarmUseCase
import com.digitalclockpro.presentation.ringing.AlarmRingingActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

/**
 * Foreground service that owns the ringing session: audio (with ramp), vibration, the
 * full-screen notification, the wake-lock and the auto-silence timeout.
 *
 * Lives independently from [AlarmRingingActivity] so swiping the activity away, rotating, or the
 * system killing the task never stops the alarm.
 *
 * ### Concurrency model
 * Snooze, dismiss, stop and the 15-minute auto-silence timer can all fire on different threads at
 * the same moment (notification action + UI button + timer). Two guards make them mutually
 * exclusive:
 *  - [terminating] — an [AtomicBoolean] CAS so only the *first* terminal request wins; later ones
 *    return immediately instead of double-snoozing or snoozing an already dismissed alarm.
 *  - [sessionMutex] — serialises the start/stop critical sections, so teardown can never
 *    interleave with a start that is still wiring up the player and wake-lock.
 *
 * State is published through [AlarmSessionManager] (not a static flow), keeping the service
 * decoupled from the view layer.
 */
@AndroidEntryPoint
class AlarmService : Service() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var sessionManager: AlarmSessionManager
    @Inject lateinit var notifications: AlarmNotifications
    @Inject lateinit var soundPlayer: AlarmSoundPlayer
    @Inject lateinit var ringtoneRepository: RingtoneRepository
    @Inject lateinit var vibrationController: VibrationController
    @Inject lateinit var snoozeAlarm: SnoozeAlarmUseCase
    @Inject lateinit var dismissAlarm: DismissAlarmUseCase

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sessionMutex = Mutex()
    /** Guards against two terminal actions (snooze / dismiss / auto-silence) racing. */
    private val terminating = AtomicBoolean(false)
    private val starting = AtomicBoolean(false)

    private var wakeLock: PowerManager.WakeLock? = null
    private var autoSilenceJob: Job? = null
    private var activeAlarmId: Long = -1L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> start(
                alarmId = intent.getLongExtra(AppIntents.EXTRA_ALARM_ID, -1L),
                isSnoozeRing = intent.getBooleanExtra(AppIntents.EXTRA_IS_SNOOZE, false)
            )
            ACTION_SNOOZE -> terminate(Reason.SNOOZE, intent.alarmIdOrActive())
            ACTION_DISMISS -> terminate(Reason.DISMISS, intent.alarmIdOrActive())
            ACTION_STOP -> terminate(Reason.STOP, intent.alarmIdOrActive())
            else -> terminate(Reason.STOP, activeAlarmId)
        }
        // START_REDELIVER_INTENT: if the system kills us mid-ring it redelivers ACTION_START with
        // the alarm id, so the alarm resumes instead of silently dying.
        return START_REDELIVER_INTENT
    }

    // ---------------------------------------------------------------- start

    private fun start(alarmId: Long, isSnoozeRing: Boolean) {
        if (alarmId <= 0L) { stopSelf(); return }
        // Re-delivery or a duplicate broadcast for the same alarm must not restart the audio.
        if (activeAlarmId == alarmId && !starting.get()) return
        if (!starting.compareAndSet(false, true)) return

        acquireWakeLock()
        scope.launch {
            sessionMutex.withLock {
                try {
                    val alarm = alarmRepository.getAlarm(alarmId)
                    if (alarm == null) {
                        Log.w(TAG, "Alarm $alarmId no longer exists – aborting ring")
                        teardown()
                        return@withLock
                    }
                    activeAlarmId = alarmId
                    terminating.set(false)
                    sessionManager.onAlarmStarted(alarm, isSnoozeRing)

                    val timeText = TimeFormatters.formatTime(
                        LocalDateTime.now(), use24h = false, showSeconds = false
                    )
                    ServiceCompat.startForeground(
                        this@AlarmService,
                        notifications.ringingNotificationId(alarmId),
                        notifications.buildRingingNotification(alarm, timeText),
                        foregroundServiceType()
                    )

                    // Direct Boot safe: falls back to the system default and finally to the
                    // tone bundled in the APK when the chosen file cannot be read yet.
                    // Null only for an explicitly silent (vibration-only) alarm.
                    ringtoneRepository.resolvePlayableAlarmUri(alarm)?.let { soundUri ->
                        soundPlayer.play(
                            scope = scope,
                            soundUri = soundUri,
                            targetVolumePercent = alarm.volumePercent,
                            rampSeconds = alarm.volumeRampSeconds
                        )
                    }
                    vibrationController.start(alarm.vibrationPattern)

                    // Belt and braces: the full-screen intent is suppressed by some OEMs when the
                    // device is unlocked, so launch the UI directly as well.
                    runCatching {
                        startActivity(AlarmRingingActivity.intent(this@AlarmService, alarmId))
                    }

                    autoSilenceJob = scope.launch {
                        delay(AUTO_SILENCE_MILLIS)
                        Log.i(TAG, "Auto-silencing alarm $alarmId after 15 min")
                        terminate(Reason.AUTO_SILENCE, alarmId)
                    }
                } finally {
                    starting.set(false)
                }
            }
        }
    }

    // ---------------------------------------------------------------- terminate

    private enum class Reason { SNOOZE, DISMISS, AUTO_SILENCE, STOP }

    /**
     * Single funnel for every way the ring can end. The CAS guarantees exactly-once semantics:
     * whichever thread arrives first performs the work, all others no-op.
     */
    private fun terminate(reason: Reason, alarmId: Long = activeAlarmId) {
        if (!terminating.compareAndSet(false, true)) {
            Log.d(TAG, "Ignoring $reason – alarm is already terminating")
            return
        }
        // Cancel the timer eagerly so it cannot queue behind the mutex and fire later.
        autoSilenceJob?.cancel()
        autoSilenceJob = null

        scope.launch {
            // NonCancellable: teardown must complete even though the service is stopping.
            withContext(NonCancellable) {
                sessionMutex.withLock {
                    if (alarmId > 0L) {
                        runCatching {
                            when (reason) {
                                Reason.SNOOZE -> {
                                    val snoozed = snoozeAlarm(alarmId)
                                    if (!snoozed) {
                                        // Snooze budget exhausted – treat it as a dismiss so the
                                        // alarm is correctly re-armed / disabled.
                                        Log.i(TAG, "No snoozes left for $alarmId – dismissing")
                                        dismissAlarm(alarmId)
                                    }
                                }
                                Reason.AUTO_SILENCE -> {
                                    if (!snoozeAlarm(alarmId)) dismissAlarm(alarmId)
                                }
                                Reason.DISMISS -> dismissAlarm(alarmId)
                                Reason.STOP -> Unit   // external stop: leave scheduling untouched
                            }
                        }.onFailure { Log.e(TAG, "Failed to apply $reason for $alarmId", it) }
                    }
                    teardown()
                }
            }
        }
    }

    /** Must only be called while holding [sessionMutex]. */
    private fun teardown() {
        soundPlayer.stop()
        vibrationController.stop()
        if (activeAlarmId > 0L) notifications.cancelRinging(activeAlarmId)
        sessionManager.onAlarmStopped()
        activeAlarmId = -1L
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Terminal actions may arrive when the service was restarted and [activeAlarmId] is not set
     * yet (e.g. a notification action after a process kill), so the id travels in the Intent too.
     */
    private fun Intent.alarmIdOrActive(): Long {
        val fromIntent = getLongExtra(AppIntents.EXTRA_ALARM_ID, -1L)
        return when {
            activeAlarmId > 0L -> activeAlarmId
            fromIntent > 0L -> fromIntent
            else -> sessionManager.ringingAlarmId ?: -1L
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun foregroundServiceType(): Int = when {
        // API 34+ requires every declared type to be justified; we declare both.
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK or
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        else -> 0
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

    override fun onDestroy() {
        super.onDestroy()
        soundPlayer.stop()
        vibrationController.stop()
        releaseWakeLock()
        // Never leave a stale "ringing" state behind if the process is torn down.
        if (activeAlarmId > 0L) sessionManager.onAlarmStopped()
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

        fun command(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }
    }
}
