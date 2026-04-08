package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.BreastfeedingEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BreastfeedingDao {
    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC")
    fun getAllSessions(): Flow<List<BreastfeedingEntity>>

    @Query("SELECT * FROM breastfeeding_sessions WHERE end_time IS NULL LIMIT 1")
    fun getActiveSession(): Flow<BreastfeedingEntity?>

    @Query("SELECT * FROM breastfeeding_sessions ORDER BY start_time DESC LIMIT 1")
    suspend fun getLastSession(): BreastfeedingEntity?

    @Insert
    suspend fun insertSession(entity: BreastfeedingEntity): Long

    @Update
    suspend fun updateSession(entity: BreastfeedingEntity)
}
