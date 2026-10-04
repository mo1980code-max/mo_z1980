package com.digitalclockpro.data.repository

import com.digitalclockpro.data.prefs.WidgetConfigDataSource
import com.digitalclockpro.domain.model.WidgetConfig
import com.digitalclockpro.domain.repository.WidgetConfigRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WidgetConfigRepositoryImpl @Inject constructor(
    private val source: WidgetConfigDataSource
) : WidgetConfigRepository {
    override fun observeConfig(appWidgetId: Int): Flow<WidgetConfig> = source.observe(appWidgetId)
    override suspend fun getConfig(appWidgetId: Int): WidgetConfig = source.get(appWidgetId)
    override suspend fun saveConfig(config: WidgetConfig) = source.save(config)
    override suspend fun deleteConfig(appWidgetId: Int) = source.delete(appWidgetId)
    override suspend fun allConfiguredIds(): List<Int> = source.allIds()
}
