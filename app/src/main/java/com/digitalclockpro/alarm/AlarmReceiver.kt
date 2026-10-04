package com.digitalclockpro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.usecase.DismissAlarmUseCase
import com.digitalclockpro.domain.usecase.SnoozeAlarmUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Entry point of every alarm event. Runs on the main thread with a ~10 s budget, so all real work
 * is handed to [AlarmService] (a foreground service) and DB work is done inside a
 * `goAsync()` pending-result block.
 */
@AndroidEntryPoint
class AlarmReceiver : BroadcastReceiver() {

    @Inject lateinit var alarmRepository: AlarmRepository
    @Inject lateinit var snoozeAlarm: SnoozeAlarmUseCase
    @Inject lateinit var dismissAlarm: DismissAlarmUseCase

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)   // required by Hilt
        val alarmId = intent.getLongExtra(AppIntents.EXTRA_ALARM_ID, -1L)
        if (alarmId <= 0L) return
        Log.d(TAG, "onReceive action=${intent.action} id=$alarmId")

        when (intent.action) {
            AppIntents.ACTION_ALARM_FIRE -> fire(context, alarmId)
            AppIntents.ACTION_ALARM_SNOOZE -> async { 
                context.stopAlarmService(alarmId)
                snoozeAlarm(alarmId)
            }
            AppIntents.ACTION_ALARM_DISMISS -> async {
                context.stopAlarmService(alarmId)
                dismissAlarm(alarmId)
            }
        }
    }

    private fun fire(context: Context, alarmId: Long) {
        val serviceIntent = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(AppIntents.EXTRA_ALARM_ID, alarmId)
        }
        // Starting an FGS from a BroadcastReceiver is allowed for exact alarms on all API levels.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun Context.stopAlarmService(alarmId: Long) {
        startService(
            Intent(this, AlarmService::class.java).apply {
                action = AlarmService.ACTION_STOP
                putExtra(AppIntents.EXTRA_ALARM_ID, alarmId)
            }
        )
    }

    /** Keeps the receiver alive while the suspend work completes. */
    private fun async(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try { block() } catch (t: Throwable) { Log.e(TAG, "async work failed", t) }
            finally { pending.finish() }
        }
    }

    private companion object { const val TAG = "AlarmReceiver" }
}
