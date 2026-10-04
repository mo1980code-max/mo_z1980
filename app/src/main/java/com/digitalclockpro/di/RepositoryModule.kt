package com.digitalclockpro.di

import com.digitalclockpro.alarm.AlarmScheduler
import com.digitalclockpro.data.repository.AlarmRepositoryImpl
import com.digitalclockpro.data.repository.PreferencesRepositoryImpl
import com.digitalclockpro.data.repository.WidgetConfigRepositoryImpl
import com.digitalclockpro.data.repository.WorldClockRepositoryImpl
import com.digitalclockpro.data.weather.WeatherRepositoryImpl
import com.digitalclockpro.domain.repository.AlarmRepository
import com.digitalclockpro.domain.repository.PreferencesRepository
import com.digitalclockpro.domain.repository.WeatherRepository
import com.digitalclockpro.domain.repository.WidgetConfigRepository
import com.digitalclockpro.domain.repository.WorldClockRepository
import com.digitalclockpro.domain.scheduler.AlarmPlanner
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds @Singleton abstract fun alarmRepository(impl: AlarmRepositoryImpl): AlarmRepository
    @Binds @Singleton abstract fun worldClockRepository(impl: WorldClockRepositoryImpl): WorldClockRepository
    @Binds @Singleton abstract fun widgetConfigRepository(impl: WidgetConfigRepositoryImpl): WidgetConfigRepository
    @Binds @Singleton abstract fun preferencesRepository(impl: PreferencesRepositoryImpl): PreferencesRepository
    @Binds @Singleton abstract fun weatherRepository(impl: WeatherRepositoryImpl): WeatherRepository
    @Binds @Singleton abstract fun alarmPlanner(impl: AlarmScheduler): AlarmPlanner
}
