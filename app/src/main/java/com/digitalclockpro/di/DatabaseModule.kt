package com.digitalclockpro.di

import android.content.Context
import androidx.room.Room
import com.digitalclockpro.data.local.AppDatabase
import com.digitalclockpro.data.local.dao.AlarmDao
import com.digitalclockpro.data.local.dao.CityDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun alarmDao(db: AppDatabase): AlarmDao = db.alarmDao()
    @Provides fun cityDao(db: AppDatabase): CityDao = db.cityDao()
}
