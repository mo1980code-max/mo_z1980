package com.digitalclockpro.data.weather

import com.digitalclockpro.di.IoDispatcher
import com.digitalclockpro.domain.model.WeatherSnapshot
import com.digitalclockpro.domain.repository.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Key-less Open-Meteo client (no API key, no tracking). Results are memory-cached for 30 min so a
 * widget refresh never performs more than two network calls per hour.
 */
@Singleton
class WeatherRepositoryImpl @Inject constructor(
    @IoDispatcher private val io: CoroutineDispatcher
) : WeatherRepository {

    private val cache = ConcurrentHashMap<String, WeatherSnapshot>()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun current(latitude: Double, longitude: Double): WeatherSnapshot? =
        withContext(io) {
            val key = "%.2f,%.2f".format(latitude, longitude)
            cache[key]?.let { cached ->
                if (System.currentTimeMillis() - cached.fetchedAtMillis < WeatherRepository.CACHE_TTL_MILLIS) {
                    return@withContext cached
                }
            }
            runCatching { fetch(latitude, longitude) }
                .getOrNull()
                ?.also { cache[key] = it }
                ?: cache[key] // serve stale data when offline
        }

    private fun fetch(lat: Double, lon: Double): WeatherSnapshot {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day" +
                "&timezone=auto"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val current = json.parseToJsonElement(body).jsonObject["current"]!!.jsonObject
            return WeatherSnapshot(
                temperatureC = current["temperature_2m"]!!.jsonPrimitive.content.toDouble(),
                weatherCode = current["weather_code"]!!.jsonPrimitive.content.toInt(),
                isDay = current["is_day"]!!.jsonPrimitive.content == "1",
                fetchedAtMillis = System.currentTimeMillis()
            )
        } finally {
            conn.disconnect()
        }
    }
}
