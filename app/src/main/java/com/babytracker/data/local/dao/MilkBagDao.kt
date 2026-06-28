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

    @Query("SELECT * FROM milk_bags ORDER BY collection_date ASC")
    suspend fun getAllBagsOnce(): List<MilkBagEntity>

    @Query("SELECT * FROM milk_bags WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MilkBagEntity?

    // Aggregate volume in a single query so callers (e.g. the partner-feed notification) don't have
    // to load a MilkBag object per id just to sum volume_ml. Ids missing from the table contribute 0
    // (no matching row), matching the per-id `?: 0` fallback the caller previously used. Caller must
    // guard against an empty id list (SQLite rejects `IN ()`).
    @Query("SELECT COALESCE(SUM(volume_ml), 0) FROM milk_bags WHERE id IN (:ids)")
    suspend fun sumVolumeForIds(ids: List<Long>): Int

    @Insert
    suspend fun insert(entity: MilkBagEntity): Long

    @Update
    suspend fun update(entity: MilkBagEntity)

    @Query(
        """
        UPDATE milk_bags
        SET collection_date = :collectionDate,
            volume_ml = :volumeMl,
            notes = :notes
        WHERE id = :id AND used_at IS NULL
        """
    )
    suspend fun updateActiveDetails(
        id: Long,
        collectionDate: Long,
        volumeMl: Int,
        notes: String?,
    ): Int

    @Delete
    suspend fun delete(entity: MilkBagEntity)
}

data class InventorySummaryRow(
    val totalMl: Int,
    val bagCount: Int,
    val oldestBagDateMs: Long?,
)
