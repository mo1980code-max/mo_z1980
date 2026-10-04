package com.digitalclockpro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.digitalclockpro.ads.AdsController
import com.digitalclockpro.alarm.AlarmNotifications
import com.digitalclockpro.domain.usecase.RescheduleAllAlarmsUseCase
import com.digitalclockpro.di.ApplicationScope
import com.digitalclockpro.widget.WidgetTickController
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class DigitalClockApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notifications: AlarmNotifications
    @Inject lateinit var rescheduleAllAlarms: RescheduleAllAlarmsUseCase
    @Inject lateinit var widgetTickController: WidgetTickController
    @Inject @ApplicationScope lateinit var appScope: CoroutineScope
    @Inject lateinit var adsController: AdsController

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifications.createChannels()
        // Screen-state aware widget ticking (no wake-locks, no polling while the screen is off).
        widgetTickController.register()
        // Defensive re-arm: covers OEM task-killers that wipe pending intents.
        appScope.launch { runCatching { rescheduleAllAlarms() } }
        // Ad machinery: activity + foreground tracking for the app-open ad gate. UMP consent
        // and MobileAds initialization are driven from MainActivity (AdsController), never
        // from here, so a consent form can never pop over the alarm ringing screen.
        adsController.attach(this)
    }
}
