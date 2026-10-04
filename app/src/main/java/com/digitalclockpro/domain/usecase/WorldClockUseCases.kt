package com.digitalclockpro.domain.usecase

import com.digitalclockpro.domain.model.CityCatalogEntry
import com.digitalclockpro.domain.model.SavedCity
import com.digitalclockpro.domain.repository.WorldClockRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSavedCitiesUseCase @Inject constructor(
    private val repository: WorldClockRepository
) {
    operator fun invoke(): Flow<List<SavedCity>> = repository.observeSavedCities()
}

class SearchCitiesUseCase @Inject constructor(
    private val repository: WorldClockRepository
) {
    suspend operator fun invoke(query: String): List<CityCatalogEntry> =
        if (query.isBlank()) emptyList() else repository.searchCatalog(query.trim())
}

class AddCityUseCase @Inject constructor(
    private val repository: WorldClockRepository
) {
    suspend operator fun invoke(entry: CityCatalogEntry) = repository.addCity(entry)
}

class RemoveCityUseCase @Inject constructor(
    private val repository: WorldClockRepository
) {
    suspend operator fun invoke(id: Long) = repository.removeCity(id)
}
