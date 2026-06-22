package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.VaccineEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaccineDao {
    @Query(
        "SELECT * FROM vaccines ORDER BY COALESCE(administered_date, scheduled_date, created_at) DESC",
    )
    fun observeAll(): Flow<List<VaccineEntity>>

    @Query("SELECT * FROM vaccines WHERE status = 'SCHEDULED' ORDER BY scheduled_date ASC")
    fun observeUpcoming(): Flow<List<VaccineEntity>>

    @Query(
        "SELECT * FROM vaccines WHERE status = 'SCHEDULED' AND scheduled_date IS NOT NULL " +
            "AND scheduled_date > :nowMs ORDER BY scheduled_date ASC",
    )
    suspend fun getScheduledFutureAfter(nowMs: Long): List<VaccineEntity>

    @Query(
        "SELECT * FROM vaccines WHERE status = 'TO_SCHEDULE' AND scheduled_date IS NOT NULL " +
            "AND scheduled_date > :nowMs ORDER BY scheduled_date ASC",
    )
    suspend fun getToScheduleFutureAfter(nowMs: Long): List<VaccineEntity>

    @Query("SELECT * FROM vaccines ORDER BY created_at ASC")
    suspend fun getAllOnce(): List<VaccineEntity>

    @Query("SELECT * FROM vaccines WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VaccineEntity?

    @Insert
    suspend fun insert(entity: VaccineEntity): Long

    @Update
    suspend fun update(entity: VaccineEntity)

    @Query("DELETE FROM vaccines WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
