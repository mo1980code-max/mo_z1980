package com.digitalclockpro.domain.model

data class UserPreferences(
    val use24Hour: Boolean = false,
    val showSecondsInApp: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val amoledBlack: Boolean = false,
    val accentColor: Long = 0xFF00E5FF,
    val defaultSnoozeMinutes: Int = 9,
    val defaultVolumeRampSeconds: Int = 30,
    val weatherEnabled: Boolean = false,
    val weatherCelsius: Boolean = true,
    val homeZoneId: String? = null,
    val keepScreenOnDashboard: Boolean = false
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }
