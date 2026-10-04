package com.digitalclockpro.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.digitalclockpro.domain.model.ThemeMode
import com.digitalclockpro.domain.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

val Context.userDataStore: DataStore<Preferences> by preferencesDataStore("user_prefs")

@Singleton
class PreferencesDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val USE_24H = booleanPreferencesKey("use_24h")
        val SHOW_SECONDS = booleanPreferencesKey("show_seconds")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val ACCENT = longPreferencesKey("accent")
        val SNOOZE = intPreferencesKey("snooze_minutes")
        val RAMP = intPreferencesKey("ramp_seconds")
        val WEATHER = booleanPreferencesKey("weather_enabled")
        val CELSIUS = booleanPreferencesKey("weather_celsius")
        val HOME_ZONE = stringPreferencesKey("home_zone")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
    }

    val preferences: Flow<UserPreferences> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            UserPreferences(
                use24Hour = p[Keys.USE_24H] ?: false,
                showSecondsInApp = p[Keys.SHOW_SECONDS] ?: true,
                themeMode = runCatching { ThemeMode.valueOf(p[Keys.THEME_MODE] ?: "SYSTEM") }
                    .getOrDefault(ThemeMode.SYSTEM),
                dynamicColor = p[Keys.DYNAMIC_COLOR] ?: true,
                amoledBlack = p[Keys.AMOLED] ?: false,
                accentColor = p[Keys.ACCENT] ?: 0xFF00E5FF,
                defaultSnoozeMinutes = p[Keys.SNOOZE] ?: 9,
                defaultVolumeRampSeconds = p[Keys.RAMP] ?: 30,
                weatherEnabled = p[Keys.WEATHER] ?: false,
                weatherCelsius = p[Keys.CELSIUS] ?: true,
                homeZoneId = p[Keys.HOME_ZONE],
                keepScreenOnDashboard = p[Keys.KEEP_SCREEN_ON] ?: false
            )
        }

    suspend fun write(value: UserPreferences) {
        dataStore.edit { p ->
            p[Keys.USE_24H] = value.use24Hour
            p[Keys.SHOW_SECONDS] = value.showSecondsInApp
            p[Keys.THEME_MODE] = value.themeMode.name
            p[Keys.DYNAMIC_COLOR] = value.dynamicColor
            p[Keys.AMOLED] = value.amoledBlack
            p[Keys.ACCENT] = value.accentColor
            p[Keys.SNOOZE] = value.defaultSnoozeMinutes
            p[Keys.RAMP] = value.defaultVolumeRampSeconds
            p[Keys.WEATHER] = value.weatherEnabled
            p[Keys.CELSIUS] = value.weatherCelsius
            value.homeZoneId?.let { p[Keys.HOME_ZONE] = it }
            p[Keys.KEEP_SCREEN_ON] = value.keepScreenOnDashboard
        }
    }
}
