package com.digitalclockpro.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.digitalclockpro.data.local.dao.AlarmDao
import com.digitalclockpro.data.local.dao.CityDao
import com.digitalclockpro.data.local.entity.AlarmEntity
import com.digitalclockpro.data.local.entity.CityEntity

@Database(
    entities = [AlarmEntity::class, CityEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun alarmDao(): AlarmDao
    abstract fun cityDao(): CityDao

    companion object { const val NAME = "digital_clock_pro.db" }
}
