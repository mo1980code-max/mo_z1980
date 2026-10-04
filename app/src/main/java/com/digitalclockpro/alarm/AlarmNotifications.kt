package com.digitalclockpro.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.digitalclockpro.R
import com.digitalclockpro.core.util.AppIntents
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.presentation.ringing.AlarmRingingActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager
) {

    fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ringing = NotificationChannel(
            CHANNEL_RINGING,
            context.getString(R.string.channel_alarm_ringing),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_alarm_ringing_desc)
            setSound(null, null)            // audio is owned by AlarmService (ramping + ExoPlayer)
            enableVibration(false)          // vibration is owned by VibrationController
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(true)
        }
        val upcoming = NotificationChannel(
            CHANNEL_UPCOMING,
            context.getString(R.string.channel_alarm_upcoming),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = context.getString(R.string.channel_alarm_upcoming_desc) }

        notificationManager.createNotificationChannels(listOf(ringing, upcoming))
    }

    /**
     * Full-screen-intent notification. On a locked device the system launches
     * [AlarmRingingActivity] directly; otherwise it shows as a heads-up banner.
     */
    fun buildRingingNotification(alarm: Alarm, timeText: String): Notification {
        val fullScreen = PendingIntent.getActivity(
            context,
            AppIntents.RC_FULLSCREEN_BASE + alarm.id.toInt(),
            AlarmRingingActivity.intent(context, alarm.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(alarm.label.ifBlank { context.getString(R.string.alarm) })
            .setContentText(timeText)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setSilent(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(
                R.drawable.ic_snooze,
                context.getString(R.string.snooze),
                broadcast(AppIntents.ACTION_ALARM_SNOOZE, alarm.id, AppIntents.RC_SNOOZE_BASE)
            )

        // A challenge must be solved in the UI, so no one-tap dismiss from the shade.
        if (alarm.challenge is com.digitalclockpro.domain.model.DismissChallenge.None) {
            builder.addAction(
                R.drawable.ic_close,
                context.getString(R.string.dismiss),
                broadcast(AppIntents.ACTION_ALARM_DISMISS, alarm.id, AppIntents.RC_DISMISS_BASE)
            )
        }
        return builder.build()
    }

    fun cancelRinging(alarmId: Long) = notificationManager.cancel(ringingNotificationId(alarmId))

    fun ringingNotificationId(alarmId: Long): Int = (RINGING_ID_BASE + alarmId).toInt()

    private fun broadcast(action: String, alarmId: Long, requestBase: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            requestBase + alarmId.toInt(),
            Intent(context, AlarmReceiver::class.java)
                .setAction(action)
                .putExtra(AppIntents.EXTRA_ALARM_ID, alarmId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        const val CHANNEL_RINGING = "alarm_ringing"
        const val CHANNEL_UPCOMING = "alarm_upcoming"
        private const val RINGING_ID_BASE = 7000L
    }
}
