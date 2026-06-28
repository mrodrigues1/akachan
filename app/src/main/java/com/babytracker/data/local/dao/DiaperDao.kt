package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.DiaperEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DiaperDao {
    @Query("SELECT * FROM diaper_changes ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<DiaperEntity>>

    @Query("SELECT * FROM diaper_changes ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<DiaperEntity>

    @Query(
        "SELECT * FROM diaper_changes WHERE timestamp BETWEEN :startMs AND :endMs ORDER BY timestamp DESC",
    )
    suspend fun getBetween(startMs: Long, endMs: Long): List<DiaperEntity>

    @Query("SELECT * FROM diaper_changes ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<DiaperEntity>

    @Insert
    suspend fun insert(entity: DiaperEntity): Long

    @Update
    suspend fun update(entity: DiaperEntity)

    @Query("DELETE FROM diaper_changes WHERE id = :id")
    suspend fun deleteById(id: Long): Int
}
