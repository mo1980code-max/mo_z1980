package com.digitalclockpro.data.repository

import com.digitalclockpro.data.prefs.PreferencesDataSource
import com.digitalclockpro.domain.model.UserPreferences
import com.digitalclockpro.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepositoryImpl @Inject constructor(
    private val source: PreferencesDataSource
) : PreferencesRepository {
    override val preferences: Flow<UserPreferences> = source.preferences
    override suspend fun update(transform: (UserPreferences) -> UserPreferences) {
        source.write(transform(source.preferences.first()))
    }
}
