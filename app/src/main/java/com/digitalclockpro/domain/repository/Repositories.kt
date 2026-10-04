package com.digitalclockpro.domain.repository

import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.model.CityCatalogEntry
import com.digitalclockpro.domain.model.SavedCity
import com.digitalclockpro.domain.model.UserPreferences
import com.digitalclockpro.domain.model.WeatherSnapshot
import com.digitalclockpro.domain.model.WidgetConfig
import kotlinx.coroutines.flow.Flow

interface AlarmRepository {
    fun observeAlarms(): Flow<List<Alarm>>
    fun observeAlarm(id: Long): Flow<Alarm?>
    suspend fun getAlarm(id: Long): Alarm?
    suspend fun getEnabledAlarms(): List<Alarm>
    suspend fun upsert(alarm: Alarm): Long
    suspend fun delete(id: Long)
    suspend fun setEnabled(id: Long, enabled: Boolean)
}

interface WorldClockRepository {
    fun observeSavedCities(): Flow<List<SavedCity>>
    suspend fun getSavedCities(): List<SavedCity>
    suspend fun addCity(entry: CityCatalogEntry): Long
    suspend fun removeCity(id: Long)
    suspend fun reorder(idsInOrder: List<Long>)
    /** Offline IANA-backed search across 10k+ cities. */
    suspend fun searchCatalog(query: String, limit: Int = 60): List<CityCatalogEntry>
}

interface WidgetConfigRepository {
    fun observeConfig(appWidgetId: Int): Flow<WidgetConfig>
    suspend fun getConfig(appWidgetId: Int): WidgetConfig
    suspend fun saveConfig(config: WidgetConfig)
    suspend fun deleteConfig(appWidgetId: Int)
    suspend fun allConfiguredIds(): List<Int>
}

interface PreferencesRepository {
    val preferences: Flow<UserPreferences>
    suspend fun update(transform: (UserPreferences) -> UserPreferences)
}

interface WeatherRepository {
    /** Cached for [CACHE_TTL_MILLIS]; returns null when offline or disabled. */
    suspend fun current(latitude: Double, longitude: Double): WeatherSnapshot?

    companion object { const val CACHE_TTL_MILLIS = 30 * 60 * 1000L }
}
