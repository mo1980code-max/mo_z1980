package com.digitalclockpro.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.digitalclockpro.domain.model.SavedCity

@Entity(
    tableName = "saved_cities",
    indices = [Index(value = ["cityName", "zoneId"], unique = true)]
)
data class CityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val cityName: String,
    val country: String,
    val zoneId: String,
    val latitude: Double,
    val longitude: Double,
    val sortOrder: Int,
    val isHome: Boolean
)

fun CityEntity.toDomain(): SavedCity = SavedCity(
    id, cityName, country, zoneId, latitude, longitude, sortOrder, isHome
)

fun SavedCity.toEntity(): CityEntity = CityEntity(
    id, cityName, country, zoneId, latitude, longitude, sortOrder, isHome
)
