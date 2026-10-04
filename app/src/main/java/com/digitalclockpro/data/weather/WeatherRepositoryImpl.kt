package com.digitalclockpro.data.weather

import android.util.Log
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
 *
 * Weather is a *decorative* widget element: it must never be able to crash a widget update or a
 * background worker. Every failure path therefore degrades to the (possibly stale) cache or null.
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
            val cached = cache[key]
            if (cached != null &&
                System.currentTimeMillis() - cached.fetchedAtMillis < WeatherRepository.CACHE_TTL_MILLIS
            ) {
                return@withContext cached
            }

            runCatching { fetch(latitude, longitude) }
                .onFailure { Log.w(TAG, "Weather fetch failed for $key", it) }
                .getOrNull()
                ?.also { cache[key] = it }
                ?: cached // serve stale data when offline / on server errors
        }

    /**
     * @return the parsed snapshot, or `null` for any non-200 response. The error body is drained
     *         and logged so the connection can be pooled instead of leaking a socket.
     */
    private fun fetch(lat: Double, lon: Double): WeatherSnapshot? {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code,is_day" +
                "&timezone=auto"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Encoding", "identity")
        }

        return try {
            // Critical: `getInputStream()` throws FileNotFoundException/IOException on 4xx/5xx.
            // Always branch on the status code first.
            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    parse(body)
                }
                else -> {
                    val error = runCatching {
                        conn.errorStream?.bufferedReader()?.use { it.readText() }
                    }.getOrNull().orEmpty().take(MAX_ERROR_LOG_CHARS)
                    Log.w(TAG, "Open-Meteo returned HTTP $code: $error")
                    null
                }
            }
        } catch (e: Exception) {
            // Covers UnknownHostException, SocketTimeoutException, SSL failures, malformed JSON…
            Log.w(TAG, "Open-Meteo request error", e)
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(body: String): WeatherSnapshot? {
        val current = json.parseToJsonElement(body).jsonObject["current"]?.jsonObject ?: run {
            Log.w(TAG, "Unexpected Open-Meteo payload: missing \"current\"")
            return null
        }
        val temperature = current["temperature_2m"]?.jsonPrimitive?.content?.toDoubleOrNull()
            ?: return null
        return WeatherSnapshot(
            temperatureC = temperature,
            weatherCode = current["weather_code"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            isDay = current["is_day"]?.jsonPrimitive?.content != "0",
            fetchedAtMillis = System.currentTimeMillis()
        )
    }

    private companion object {
        const val TAG = "WeatherRepository"
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 8_000
        const val MAX_ERROR_LOG_CHARS = 300
    }
}
