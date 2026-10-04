package com.digitalclockpro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.digitalclockpro.core.util.AppIntents
import dagger.hilt.android.AndroidEntryPoint

/**
 * Entry point of every alarm event. Runs on the main thread with a ~10 s budget, so **no work is
 * done here**: every action is forwarded to [AlarmService], which owns the ringing session and
 * serialises snooze / dismiss / auto-silence behind a single mutex.
 *
 * Previously this receiver invoked the snooze/dismiss use-cases itself *and* told the service to
 * stop, which meant the same alarm could be mutated twice from two threads (receiver + service
 * timeout). Routing everything through the service removes that race entirely.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)   // required by Hilt
        val alarmId = intent.getLongExtra(AppIntents.EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0L) return
        Log.d(TAG, "onReceive action=${intent.action} id=$alarmId")

        when (intent.action) {
            AppIntents.ACTION_ALARM_FIRE -> context.startRinging(
                alarmId = alarmId,
                isSnoozeRing = intent.getBooleanExtra(AppIntents.EXTRA_IS_SNOOZE, false)
            )
            AppIntents.ACTION_ALARM_SNOOZE -> context.command(AlarmService.ACTION_SNOOZE, alarmId)
            AppIntents.ACTION_ALARM_DISMISS -> context.command(AlarmService.ACTION_DISMISS, alarmId)
        }
    }

    private fun Context.startRinging(alarmId: Long, isSnoozeRing: Boolean) {
        val serviceIntent = Intent(this, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AppIntents.EXTRA_ALARM_ID, alarmId)
            putExtra(AppIntents.EXTRA_IS_SNOOZE, isSnoozeRing)
        }
        // Starting an FGS from a BroadcastReceiver is allowed for exact alarms on all API levels.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun Context.command(action: String, alarmId: Long) {
        startService(
            Intent(this, AlarmService::class.java)
                .setAction(action)
                .putExtra(AppIntents.EXTRA_ALARM_ID, alarmId)
        )
    }

    private companion object { const val TAG = "AlarmReceiver" }
}
