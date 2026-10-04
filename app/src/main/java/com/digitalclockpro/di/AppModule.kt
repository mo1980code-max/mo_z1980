package com.digitalclockpro.di

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.os.PowerManager
import android.os.Vibrator
import android.os.VibratorManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.digitalclockpro.data.prefs.userDataStore
import com.digitalclockpro.data.prefs.widgetDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import com.digitalclockpro.clockengine.BatteryLevelFilter
import com.digitalclockpro.clockengine.RedrawGate
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO
    @Provides @DefaultDispatcher fun default(): CoroutineDispatcher = Dispatchers.Default
    @Provides @MainDispatcher fun main(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides @Singleton @ApplicationScope
    fun appScope(@IoDispatcher dispatcher: CoroutineDispatcher): CoroutineScope =
        CoroutineScope(SupervisorJob() + dispatcher)

    @Provides @Singleton
    fun userDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.userDataStore

    @Provides @Singleton @Named("widget")
    fun widgetDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.widgetDataStore

    @Provides @Singleton
    fun alarmManager(@ApplicationContext c: Context): AlarmManager =
        c.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    @Provides @Singleton
    fun notificationManager(@ApplicationContext c: Context): NotificationManager =
        c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    @Provides @Singleton
    fun powerManager(@ApplicationContext c: Context): PowerManager =
        c.getSystemService(Context.POWER_SERVICE) as PowerManager

    @Provides @Singleton
    fun vibrator(@ApplicationContext c: Context): Vibrator =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            (c.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            c.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
}

@Module
@InstallIn(SingletonComponent::class)
object WidgetSystemModule {
    @Provides @Singleton
    fun appWidgetManager(@ApplicationContext c: Context): android.appwidget.AppWidgetManager =
        android.appwidget.AppWidgetManager.getInstance(c)

    /**
     * Process-wide, and it has to be: widget providers are `BroadcastReceiver`s, so a new
     * instance is constructed for every broadcast. Holding the cache in the provider would
     * reset it on each delivery and the gate would never match anything.
     */
    @Provides @Singleton
    fun redrawGate(): RedrawGate = RedrawGate()

    @Provides @Singleton
    fun batteryLevelFilter(): BatteryLevelFilter = BatteryLevelFilter()
}
