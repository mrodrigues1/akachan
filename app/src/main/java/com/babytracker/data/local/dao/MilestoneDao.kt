package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.MilestoneEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {

    @Insert
    suspend fun insert(milestone: MilestoneEntity): Long

    @Update
    suspend fun update(milestone: MilestoneEntity)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteById(id: Long)

    // Newest first. Timed entries sort before time-less ones on the same day
    // (NULL minute-of-day sorts last).
    @Query(
        "SELECT * FROM milestones " +
            "ORDER BY date_epoch_day DESC, time_minute_of_day IS NULL, time_minute_of_day DESC, id DESC",
    )
    fun getAll(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE id = :id")
    fun getById(id: Long): Flow<MilestoneEntity?>

    @Query("SELECT * FROM milestones")
    suspend fun getAllOnce(): List<MilestoneEntity>
}
