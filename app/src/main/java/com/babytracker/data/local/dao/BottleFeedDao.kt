package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.babytracker.data.local.entity.BottleFeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BottleFeedDao {
    @Query("SELECT * FROM bottle_feeds ORDER BY timestamp DESC")
    fun getAll(): Flow<List<BottleFeedEntity>>

    @Query("SELECT * FROM bottle_feeds WHERE timestamp >= :startMs ORDER BY timestamp DESC")
    fun getSince(startMs: Long): Flow<List<BottleFeedEntity>>

    @Query("SELECT * FROM bottle_feeds ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<BottleFeedEntity>

    @Insert
    suspend fun insert(entity: BottleFeedEntity): Long

    @Query(
        """
        UPDATE bottle_feeds
        SET timestamp = :timestamp,
            volume_ml = :volumeMl,
            type = :type,
            linked_milk_bag_id = :linkedMilkBagId,
            notes = :notes
        WHERE id = :id
        """,
    )
    suspend fun updateDetails(
        id: Long,
        timestamp: Long,
        volumeMl: Int,
        type: String,
        linkedMilkBagId: Long?,
        notes: String?,
    ): Int

    @Delete
    suspend fun delete(entity: BottleFeedEntity)
}
