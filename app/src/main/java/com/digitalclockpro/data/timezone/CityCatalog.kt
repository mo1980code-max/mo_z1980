package com.digitalclockpro.data.timezone

import android.content.Context
import com.digitalclockpro.domain.model.CityCatalogEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fully offline city/timezone catalogue.
 *
 *  1. Every IANA zone shipped with the device ([ZoneId.getAvailableZoneIds]) is turned into a
 *     searchable entry ("Africa/Cairo" -> "Cairo", Africa).
 *  2. `assets/cities_extra.tsv` adds thousands of additional cities / alternate + localized
 *     spellings that map onto one of those zones (name, country, zoneId, lat, lon).
 *
 * The index is built lazily on first search and kept in memory (~1 MB for 10k entries).
 */
@Singleton
class CityCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val entries: List<CityCatalogEntry> by lazy { buildIndex() }
    private val normalizedKeys: List<String> by lazy {
        entries.map { "${it.cityName} ${it.country} ${it.zoneId}".normalize() }
    }

    fun all(): List<CityCatalogEntry> = entries

    fun search(query: String, limit: Int = 60): List<CityCatalogEntry> {
        val q = query.normalize()
        if (q.isEmpty()) return emptyList()
        val starts = ArrayList<CityCatalogEntry>(limit)
        val contains = ArrayList<CityCatalogEntry>(limit)
        for (i in entries.indices) {
            val key = normalizedKeys[i]
            when {
                key.startsWith(q) -> starts += entries[i]
                key.contains(q) -> contains += entries[i]
            }
            if (starts.size >= limit) break
        }
        return (starts + contains).distinctBy { it.cityName + it.zoneId }.take(limit)
    }

    fun findByZone(zoneId: String): CityCatalogEntry? = entries.firstOrNull { it.zoneId == zoneId }

    private fun buildIndex(): List<CityCatalogEntry> {
        val fromZones = ZoneId.getAvailableZoneIds()
            .asSequence()
            .filter { it.contains('/') && !it.startsWith("Etc/") && !it.startsWith("SystemV") }
            .map { id ->
                val parts = id.split('/')
                CityCatalogEntry(
                    cityName = parts.last().replace('_', ' '),
                    country = parts.first().replace('_', ' '),
                    zoneId = id,
                    latitude = 0.0,
                    longitude = 0.0
                )
            }
            .toList()

        val fromAssets = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
                lines.mapNotNull { parse(it) }.toList()
            }
        }.getOrDefault(emptyList())

        return (fromAssets + fromZones)
            .distinctBy { it.cityName.lowercase(Locale.ROOT) + "|" + it.zoneId }
            .sortedBy { it.cityName }
    }

    private fun parse(line: String): CityCatalogEntry? {
        if (line.isBlank() || line.startsWith("#")) return null
        val c = line.split('\t')
        if (c.size < 3) return null
        val zone = c[2].trim()
        if (runCatching { ZoneId.of(zone) }.isFailure) return null
        return CityCatalogEntry(
            cityName = c[0].trim(),
            country = c[1].trim(),
            zoneId = zone,
            latitude = c.getOrNull(3)?.toDoubleOrNull() ?: 0.0,
            longitude = c.getOrNull(4)?.toDoubleOrNull() ?: 0.0
        )
    }

    private fun String.normalize(): String = trim().lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replace("-", " ")

    private companion object { const val ASSET_NAME = "cities_extra.tsv" }
}
