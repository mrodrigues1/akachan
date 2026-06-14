package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babytracker.data.local.entity.GrowthMeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GrowthMeasurementDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: GrowthMeasurementEntity): Long

    @Query("SELECT * FROM growth_measurements ORDER BY taken_at DESC")
    fun getAll(): Flow<List<GrowthMeasurementEntity>>

    @Query("SELECT * FROM growth_measurements WHERE type = :type ORDER BY taken_at ASC")
    fun getByType(type: String): Flow<List<GrowthMeasurementEntity>>

    @Query("DELETE FROM growth_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)
}
