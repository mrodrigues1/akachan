package com.babytracker.domain.repository

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BottleFeedRepository {
    fun getAll(): Flow<List<BottleFeed>>
    fun getSince(start: Instant): Flow<List<BottleFeed>>
    suspend fun getById(id: Long): BottleFeed?
    suspend fun insert(feed: BottleFeed): Long
    suspend fun updateDetails(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
    ): Boolean
    suspend fun updateDetailsWithInventory(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
        usedAt: Instant,
    ): Boolean
    suspend fun deleteWithInventoryRestore(feed: BottleFeed): Boolean
    suspend fun delete(feed: BottleFeed)
}
