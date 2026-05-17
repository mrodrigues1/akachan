package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.babytracker.data.local.entity.MilkBagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MilkBagDao {
    @Query("SELECT * FROM milk_bags WHERE used_at IS NULL ORDER BY collection_date ASC")
    fun getActiveBags(): Flow<List<MilkBagEntity>>

    @Query("SELECT * FROM milk_bags ORDER BY collection_date DESC")
    fun getAllBags(): Flow<List<MilkBagEntity>>

    @Query(
        """
        SELECT COALESCE(SUM(volume_ml), 0) AS totalMl,
               COUNT(*) AS bagCount,
               MIN(collection_date) AS oldestBagDateMs
        FROM milk_bags
        WHERE used_at IS NULL
        """
    )
    fun getActiveSummary(): Flow<InventorySummaryRow>

    @Insert
    suspend fun insert(entity: MilkBagEntity): Long

    @Update
    suspend fun update(entity: MilkBagEntity)

    @Delete
    suspend fun delete(entity: MilkBagEntity)
}

data class InventorySummaryRow(
    val totalMl: Int,
    val bagCount: Int,
    val oldestBagDateMs: Long?,
)
