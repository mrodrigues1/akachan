package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.babytracker.data.local.entity.BottleFeedEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BottleFeedDao {
    @Query("SELECT * FROM bottle_feeds ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<BottleFeedEntity>>

    @Query("SELECT * FROM bottle_feeds WHERE timestamp >= :startMs ORDER BY timestamp DESC")
    suspend fun getSinceOnce(startMs: Long): List<BottleFeedEntity>

    @Query("SELECT * FROM bottle_feeds ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<BottleFeedEntity>

    @Query("SELECT * FROM bottle_feeds ORDER BY timestamp ASC")
    suspend fun getAllOnce(): List<BottleFeedEntity>

    @Query("SELECT * FROM bottle_feeds WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BottleFeedEntity?

    @Query("SELECT * FROM bottle_feeds WHERE client_id = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): BottleFeedEntity?

    @Transaction
    suspend fun insertWithBagConsume(entity: BottleFeedEntity, consumedBagId: Long?, usedAt: Long): Long {
        // Feed truth outranks the stash link — if the bag was consumed/deleted
        // meanwhile, insert the feed without the link.
        val linkBag = consumedBagId != null && isMilkBagActive(consumedBagId)
        val id = insert(entity.copy(linkedMilkBagId = if (linkBag) consumedBagId else null))
        if (linkBag) markMilkBagUsed(consumedBagId, usedAt)
        return id
    }

    @Transaction
    suspend fun updateDetailsWithInventory(
        id: Long,
        timestamp: Long,
        volumeMl: Int,
        type: String,
        linkedMilkBagId: Long?,
        notes: String?,
        usedAt: Long,
    ): Boolean {
        val existing = getById(id) ?: return false
        val nextLinkedMilkBagId = linkedMilkBagId.takeIf { type == "BREAST_MILK" }
        if (nextLinkedMilkBagId != null && nextLinkedMilkBagId != existing.linkedMilkBagId) {
            check(isMilkBagActive(nextLinkedMilkBagId)) { "Milk bag no longer exists or is already used" }
        }

        val updated = updateDetails(id, timestamp, volumeMl, type, nextLinkedMilkBagId, notes)
        if (updated == 0) return false

        if (existing.linkedMilkBagId != nextLinkedMilkBagId) {
            existing.linkedMilkBagId?.let { restoreMilkBagIfUnreferenced(it) }
            nextLinkedMilkBagId?.let { markMilkBagUsed(it, usedAt) }
        }
        return true
    }

    @Transaction
    suspend fun deleteWithInventoryRestore(id: Long): Boolean {
        val existing = getById(id) ?: return false
        val deleted = deleteById(id)
        if (deleted == 0) return false

        existing.linkedMilkBagId?.let { restoreMilkBagIfUnreferenced(it) }
        return true
    }

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

    @Query("SELECT EXISTS(SELECT 1 FROM milk_bags WHERE id = :id AND used_at IS NULL)")
    suspend fun isMilkBagActive(id: Long): Boolean

    @Query(
        """
        UPDATE milk_bags
        SET used_at = NULL
        WHERE id = :id
          AND NOT EXISTS (
              SELECT 1 FROM bottle_feeds WHERE linked_milk_bag_id = :id
          )
        """,
    )
    suspend fun restoreMilkBagIfUnreferenced(id: Long): Int

    @Query("UPDATE milk_bags SET used_at = :usedAt WHERE id = :id AND used_at IS NULL")
    suspend fun markMilkBagUsed(id: Long, usedAt: Long): Int

    @Query("DELETE FROM bottle_feeds WHERE id = :id")
    suspend fun deleteById(id: Long): Int

    @Delete
    suspend fun delete(entity: BottleFeedEntity)
}
