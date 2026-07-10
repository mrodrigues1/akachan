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
    @Query("SELECT * FROM pumping_sessions ORDER BY start_time DESC LIMIT :limit")
    fun getRecentSessionsFlow(limit: Int): Flow<List<PumpingEntity>>

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

    @Query(
        """
        UPDATE pumping_sessions
        SET end_time = :endTime, volume_ml = :volumeMl, paused_duration_ms = :pausedDurationMs
        WHERE id = :id AND end_time IS NULL
        """,
    )
    suspend fun updateEndTimeIfActive(id: Long, endTime: Long, volumeMl: Int?, pausedDurationMs: Long): Int

    @Delete
    suspend fun delete(entity: PumpingEntity)
}
