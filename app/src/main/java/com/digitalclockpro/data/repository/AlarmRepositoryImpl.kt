package com.digitalclockpro.data.repository

import com.digitalclockpro.data.local.dao.AlarmDao
import com.digitalclockpro.data.local.entity.toDomain
import com.digitalclockpro.data.local.entity.toEntity
import com.digitalclockpro.di.IoDispatcher
import com.digitalclockpro.domain.model.Alarm
import com.digitalclockpro.domain.repository.AlarmRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlarmRepositoryImpl @Inject constructor(
    private val dao: AlarmDao,
    @IoDispatcher private val io: CoroutineDispatcher
) : AlarmRepository {

    override fun observeAlarms(): Flow<List<Alarm>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }.flowOn(io)

    override fun observeAlarm(id: Long): Flow<Alarm?> =
        dao.observeById(id).map { it?.toDomain() }.flowOn(io)

    override suspend fun getAlarm(id: Long): Alarm? = withContext(io) { dao.getById(id)?.toDomain() }

    override suspend fun getEnabledAlarms(): List<Alarm> =
        withContext(io) { dao.getEnabled().map { it.toDomain() } }

    override suspend fun upsert(alarm: Alarm): Long = withContext(io) {
        val rowId = dao.upsert(alarm.toEntity())
        if (rowId == -1L || alarm.id != 0L) alarm.id else rowId
    }

    override suspend fun delete(id: Long) = withContext(io) { dao.deleteById(id) }

    override suspend fun setEnabled(id: Long, enabled: Boolean) =
        withContext(io) { dao.setEnabled(id, enabled) }
}
