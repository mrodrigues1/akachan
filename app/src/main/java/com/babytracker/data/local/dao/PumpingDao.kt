package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.PumpingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PumpingDao {
    @Query("SELECT * FROM pumping_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<PumpingEntity>>

    @Query("SELECT * FROM pumping_sessions WHERE end_time IS NULL LIMIT 1")
    fun getActiveSession(): Flow<PumpingEntity?>

    @Query("SELECT * FROM pumping_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): PumpingEntity?

    @Query("SELECT * FROM pumping_sessions ORDER BY start_time ASC")
    suspend fun getAllSessionsOnce(): List<PumpingEntity>

    @Insert
    suspend fun insert(entity: PumpingEntity): Long

    @Update
    suspend fun update(entity: PumpingEntity)

    @Delete
    suspend fun delete(entity: PumpingEntity)
}
