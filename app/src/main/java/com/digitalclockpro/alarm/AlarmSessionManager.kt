package com.digitalclockpro.alarm

import android.content.Context
import android.content.SharedPreferences
import com.digitalclockpro.di.ApplicationScope
import com.digitalclockpro.di.deviceProtectedStorageContextCompat
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.repository.AlarmRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for "is an alarm ringing right now, and which one?".
 *
 * Replaces the previous static `companion object` flow inside `AlarmService`, which coupled the
 * service to the view layer, leaked whatever the flow retained for the lifetime of the process,
 * and lost its value whenever the process was killed and restarted by the system.
 *
 * Two layers of state:
 *  - [session] — hot in-memory [StateFlow] every consumer (UI, widgets, tests) collects.
 *  - a tiny device-protected [SharedPreferences] record, so that if the process is killed while
 *    the alarm is still ringing (OEM task killer, low memory) the restarted process can rebuild
 *    the session instead of showing an empty ringing screen. Device-protected storage is used so
 *    this works during Direct Boot, exactly like the alarm database.
 */
@Singleton
class AlarmSessionManager @Inject constructor(
    @ApplicationContext context: Context,
    private val alarmRepository: AlarmRepository,
    @ApplicationScope private val scope: CoroutineScope
) {

    /** Snapshot of the currently ringing alarm. */
    data class RingingSession(
        val alarm: Alarm,
        val startedAtMillis: Long,
        val isSnoozeRing: Boolean
    )

    private val prefs: SharedPreferences = context
        .deviceProtectedStorageContextCompat()
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _session = MutableStateFlow<RingingSession?>(null)

    /** Null when nothing is ringing. */
    val session: StateFlow<RingingSession?> = _session.asStateFlow()

    val isRinging: Boolean get() = _session.value != null

    /** Id of the ringing alarm, or `null`. Cheap synchronous access for receivers/services. */
    val ringingAlarmId: Long? get() = _session.value?.alarm?.id

    init {
        // Rebuild the session after a process restart so a relaunched ringing screen is populated.
        restorePersistedSession()
    }

    fun onAlarmStarted(alarm: Alarm, isSnoozeRing: Boolean) {
        _session.value = RingingSession(
            alarm = alarm,
            startedAtMillis = System.currentTimeMillis(),
            isSnoozeRing = isSnoozeRing
        )
        prefs.edit()
            .putLong(KEY_ALARM_ID, alarm.id)
            .putLong(KEY_STARTED_AT, System.currentTimeMillis())
            .putBoolean(KEY_IS_SNOOZE, isSnoozeRing)
            .apply()
    }

    fun onAlarmStopped() {
        _session.value = null
        prefs.edit().clear().apply()
    }

    /** Keeps the emitted alarm in sync when the entity changes mid-ring (e.g. snooze counter). */
    fun updateAlarm(alarm: Alarm) {
        _session.value = _session.value?.takeIf { it.alarm.id == alarm.id }?.copy(alarm = alarm)
    }

    private fun restorePersistedSession() {
        val alarmId = prefs.getLong(KEY_ALARM_ID, -1L)
        if (alarmId <= 0L) return

        val startedAt = prefs.getLong(KEY_STARTED_AT, 0L)
        // A record older than the auto-silence window is stale (e.g. the device rebooted while
        // ringing) — drop it rather than resurrecting an alarm the user already slept through.
        if (System.currentTimeMillis() - startedAt > STALE_SESSION_MILLIS) {
            prefs.edit().clear().apply()
            return
        }

        scope.launch {
            val alarm = alarmRepository.getAlarm(alarmId)
            if (alarm == null) {
                prefs.edit().clear().apply()
                return@launch
            }
            _session.value = RingingSession(
                alarm = alarm,
                startedAtMillis = startedAt,
                isSnoozeRing = prefs.getBoolean(KEY_IS_SNOOZE, false)
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "alarm_session"
        const val KEY_ALARM_ID = "ringing_alarm_id"
        const val KEY_STARTED_AT = "ringing_started_at"
        const val KEY_IS_SNOOZE = "ringing_is_snooze"
        const val STALE_SESSION_MILLIS = 16 * 60 * 1000L
    }
}
