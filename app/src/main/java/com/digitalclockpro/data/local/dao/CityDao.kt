package com.digitalclockpro.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.digitalclockpro.data.local.entity.CityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    @Query("SELECT * FROM saved_cities ORDER BY isHome DESC, sortOrder ASC")
    fun observeAll(): Flow<List<CityEntity>>

    @Query("SELECT * FROM saved_cities ORDER BY isHome DESC, sortOrder ASC")
    suspend fun getAll(): List<CityEntity>

    @Query("SELECT * FROM saved_cities WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<CityEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(city: CityEntity): Long

    @Query("DELETE FROM saved_cities WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COALESCE(MAX(sortOrder), 0) FROM saved_cities")
    suspend fun maxSortOrder(): Int

    @Query("UPDATE saved_cities SET sortOrder = :order WHERE id = :id")
    suspend fun updateOrder(id: Long, order: Int)

    @Transaction
    suspend fun reorder(idsInOrder: List<Long>) {
        idsInOrder.forEachIndexed { index, id -> updateOrder(id, index) }
    }
}
