package com.digitalclockpro.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Persists which OEM power-management steps the user has already ticked off. */
@Singleton
class OemChecklistDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val key = stringSetPreferencesKey("oem_completed_steps")

    val completedSteps: Flow<Set<String>> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { it[key] ?: emptySet() }

    suspend fun setCompleted(id: String, completed: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            prefs[key] = if (completed) current + id else current - id
        }
    }
}
