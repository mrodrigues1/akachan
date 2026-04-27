package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.SleepEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SleepDao {
    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC")
    fun getAllRecords(): Flow<List<SleepEntity>>

    @Query("SELECT * FROM sleep_records WHERE start_time >= :sinceMillis AND end_time IS NOT NULL ORDER BY start_time ASC")
    suspend fun getCompletedRecordsSince(sinceMillis: Long): List<SleepEntity>

    @Query("SELECT * FROM sleep_records ORDER BY start_time DESC LIMIT :limit")
    suspend fun getRecentRecords(limit: Int): List<SleepEntity>

    @Insert
    suspend fun insertRecord(entity: SleepEntity): Long

    @Update
    suspend fun updateRecord(entity: SleepEntity)
}
