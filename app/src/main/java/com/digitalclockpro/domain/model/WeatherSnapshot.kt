package com.digitalclockpro.domain.model

data class WeatherSnapshot(
    val temperatureC: Double,
    val weatherCode: Int,
    val isDay: Boolean,
    val fetchedAtMillis: Long
) {
    fun temperature(celsius: Boolean): Int =
        if (celsius) Math.round(temperatureC).toInt()
        else Math.round(temperatureC * 9 / 5 + 32).toInt()

    /** WMO weather interpretation codes -> short label. */
    val condition: String
        get() = when (weatherCode) {
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            in 51..57 -> "Drizzle"
            in 61..67 -> "Rain"
            in 71..77 -> "Snow"
            in 80..82 -> "Showers"
            in 95..99 -> "Thunderstorm"
            else -> "—"
        }
}
