package com.digitalclockpro.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.digitalclockpro.domain.usecase.RescheduleAllAlarmsUseCase
import com.digitalclockpro.widget.WidgetUpdater
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Restores every enabled alarm after:
 *  - device reboot (BOOT_COMPLETED / LOCKED_BOOT_COMPLETED for Direct Boot devices)
 *  - app update (MY_PACKAGE_REPLACED – pending intents are wiped)
 *  - manual time / timezone change (next trigger must be recomputed)
 *  - the user granting or revoking "Alarms & reminders" on Android 12+
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var rescheduleAllAlarms: RescheduleAllAlarmsUseCase
    @Inject lateinit var widgetUpdater: WidgetUpdater

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)   // required by Hilt
        Log.i(TAG, "Restoring alarms after ${intent.action}")
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                rescheduleAllAlarms()
                widgetUpdater.refreshAll()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to restore alarms", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object { const val TAG = "BootReceiver" }
}
