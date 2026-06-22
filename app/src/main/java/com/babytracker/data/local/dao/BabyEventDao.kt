package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babytracker.data.local.entity.BabyEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BabyEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: BabyEventEntity)

    @Query("SELECT * FROM baby_events WHERE timestamp >= :cutoffMs ORDER BY timestamp DESC")
    fun getEventsSince(cutoffMs: Long): Flow<List<BabyEventEntity>>
}
