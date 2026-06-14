package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babytracker.data.local.entity.MilestoneAchievementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilestoneDao {

    /** Upserts by milestone: the unique index on `milestone` makes REPLACE overwrite the prior row. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(achievement: MilestoneAchievementEntity): Long

    @Query("SELECT * FROM milestone_achievements")
    fun getAll(): Flow<List<MilestoneAchievementEntity>>

    @Query("DELETE FROM milestone_achievements WHERE milestone = :milestone")
    suspend fun deleteByMilestone(milestone: String)
}
