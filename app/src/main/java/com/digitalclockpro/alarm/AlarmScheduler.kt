package com.digitalclockpro.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.scheduler.AlarmPlanner
import com.digitalclockpro.presentation.MainActivity
import com.digitalclockpro.widget.WidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact-alarm scheduling that survives Doze, reboots and Android 12+ permission changes.
 *
 * Strategy per alarm:
 *  - `setAlarmClock()` when we can (it is the only API the system never defers; it also surfaces
 *    the alarm in the status bar and in `AlarmManager.getNextAlarmClock()`), with a
 *    `setExactAndAllowWhileIdle()` fallback when the user revoked SCHEDULE_EXACT_ALARM, and a
 *    final `setWindow()` inexact fallback so the alarm still rings (slightly late) instead of
 *    never firing.
 */
@Singleton
class AlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmManager: AlarmManager,
    private val widgetUpdater: WidgetUpdater
) : AlarmPlanner {

    override fun schedule(alarm: Alarm) {
        if (!alarm.enabled) { cancel(alarm.id); return }
        scheduleAt(alarm, alarm.nextTriggerAtMillis(), isSnooze = false)
    }

    override fun scheduleSnooze(alarm: Alarm, minutes: Int) {
        val triggerAt = System.currentTimeMillis() + minutes * 60_000L
        scheduleAt(alarm, triggerAt, isSnooze = true)
    }

    private fun scheduleAt(alarm: Alarm, triggerAtMillis: Long, isSnooze: Boolean) {
        val firePendingIntent = firePendingIntent(alarm.id, isSnooze)
        try {
            if (canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent()),
                    firePendingIntent
                )
            } else {
                // Exact alarms revoked (Android 12+). Still ask for an exact-while-idle slot;
                // the OS grants best-effort delivery and we prompt the user in Settings.
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP, triggerAtMillis, firePendingIntent
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm denied, falling back to a 5-minute window", e)
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP, triggerAtMillis, 5 * 60_000L, firePendingIntent
            )
        }
        Log.d(TAG, "Alarm ${alarm.id} scheduled for $triggerAtMillis (snooze=$isSnooze)")
    }

    override fun cancel(alarmId: Long) {
        alarmManager.cancel(firePendingIntent(alarmId, isSnooze = false))
        alarmManager.cancel(firePendingIntent(alarmId, isSnooze = true))
    }

    override fun canScheduleExactAlarms(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) alarmManager.canScheduleExactAlarms() else true

    override fun refreshNextAlarmIndicators() = widgetUpdater.refreshAll()

    private fun firePendingIntent(alarmId: Long, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AppIntents.ACTION_ALARM_FIRE
            putExtra(AppIntents.EXTRA_ALARM_ID, alarmId)
            putExtra(AppIntents.EXTRA_IS_SNOOZE, isSnooze)
            // Make the Intent unique so PendingIntent.FLAG_UPDATE_CURRENT targets the right alarm.
            data = android.net.Uri.parse("dcp://alarm/$alarmId/${if (isSnooze) "snooze" else "main"}")
        }
        return PendingIntent.getBroadcast(
            context,
            AppIntents.RC_ALARM_BASE + alarmId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /**
     * Tapping the status-bar alarm icon opens the alarm list. Marked EXTRA_FROM_ALARM so the
     * app-open ad is suppressed: this is an alarm-originated open, and AdPolicy vetoes those.
     */
    private fun showIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        AppIntents.RC_ALARM_BASE,
        Intent(context, MainActivity::class.java)
            .putExtra(AppIntents.EXTRA_START_DESTINATION, "alarms")
            .putExtra(AppIntents.EXTRA_FROM_ALARM, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private companion object { const val TAG = "AlarmScheduler" }
}
