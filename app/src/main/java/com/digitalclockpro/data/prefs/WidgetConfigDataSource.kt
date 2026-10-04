package com.digitalclockpro.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.digitalclockpro.domain.model.WidgetConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

val Context.widgetDataStore: DataStore<Preferences> by preferencesDataStore("widget_configs")

/** One JSON blob per appWidgetId – cheap to read from a BroadcastReceiver via runBlocking. */
@Singleton
class WidgetConfigDataSource @Inject constructor(
    @Named("widget") private val dataStore: DataStore<Preferences>
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun key(id: Int) = stringPreferencesKey("widget_$id")

    fun observe(appWidgetId: Int): Flow<WidgetConfig> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> decode(prefs[key(appWidgetId)], appWidgetId) }

    suspend fun get(appWidgetId: Int): WidgetConfig = observe(appWidgetId).first()

    suspend fun save(config: WidgetConfig) {
        dataStore.edit { it[key(config.appWidgetId)] = json.encodeToString(config) }
    }

    suspend fun delete(appWidgetId: Int) {
        dataStore.edit { it.remove(key(appWidgetId)) }
    }

    suspend fun allIds(): List<Int> = dataStore.data.first().asMap().keys
        .mapNotNull { it.name.removePrefix("widget_").toIntOrNull() }

    private fun decode(raw: String?, id: Int): WidgetConfig =
        raw?.let { runCatching { json.decodeFromString<WidgetConfig>(it) }.getOrNull() }
            ?: WidgetConfig(appWidgetId = id)
}
