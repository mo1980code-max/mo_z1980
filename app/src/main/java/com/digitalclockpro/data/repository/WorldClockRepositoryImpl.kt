package com.digitalclockpro.data.repository

import com.digitalclockpro.data.local.dao.CityDao
import com.digitalclockpro.data.local.entity.CityEntity
import com.digitalclockpro.data.local.entity.toDomain
import com.digitalclockpro.data.timezone.CityCatalog
import com.digitalclockpro.di.IoDispatcher
import com.digitalclockpro.domain.model.CityCatalogEntry
import com.digitalclockpro.domain.model.SavedCity
import com.digitalclockpro.domain.repository.WorldClockRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WorldClockRepositoryImpl @Inject constructor(
    private val dao: CityDao,
    private val catalog: CityCatalog,
    @IoDispatcher private val io: CoroutineDispatcher
) : WorldClockRepository {

    override fun observeSavedCities(): Flow<List<SavedCity>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(io)

    override suspend fun getSavedCities(): List<SavedCity> =
        withContext(io) { dao.getAll().map { it.toDomain() } }

    override suspend fun addCity(entry: CityCatalogEntry): Long = withContext(io) {
        dao.insert(
            CityEntity(
                cityName = entry.cityName,
                country = entry.country,
                zoneId = entry.zoneId,
                latitude = entry.latitude,
                longitude = entry.longitude,
                sortOrder = dao.maxSortOrder() + 1,
                isHome = false
            )
        )
    }

    override suspend fun removeCity(id: Long) = withContext(io) { dao.deleteById(id) }

    override suspend fun reorder(idsInOrder: List<Long>) = withContext(io) { dao.reorder(idsInOrder) }

    override suspend fun searchCatalog(query: String, limit: Int): List<CityCatalogEntry> =
        withContext(io) { catalog.search(query, limit) }
}
