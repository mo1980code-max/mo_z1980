package com.digitalclockpro.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.digitalclockpro.data.local.entity.AlarmEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlarmDao {
    @Query("SELECT * FROM alarms ORDER BY hour ASC, minute ASC")
    fun observeAll(): Flow<List<AlarmEntity>>

    @Query("SELECT * FROM alarms WHERE id = :id")
    fun observeById(id: Long): Flow<AlarmEntity?>

    @Query("SELECT * FROM alarms WHERE id = :id")
    suspend fun getById(id: Long): AlarmEntity?

    @Query("SELECT * FROM alarms WHERE enabled = 1")
    suspend fun getEnabled(): List<AlarmEntity>

    @Upsert
    suspend fun upsert(alarm: AlarmEntity): Long

    @Query("UPDATE alarms SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM alarms WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Delete
    suspend fun delete(alarm: AlarmEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(alarms: List<AlarmEntity>)
}
