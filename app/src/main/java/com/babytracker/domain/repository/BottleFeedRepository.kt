package com.babytracker.domain.repository

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BottleFeedRepository {
    /** Observes the newest [limit] feeds, newest first. */
    fun getRecentFlow(limit: Int): Flow<List<BottleFeed>>
    fun getSince(start: Instant): Flow<List<BottleFeed>>

    /** The [limit] most recent feeds, newest first. Bounded one-shot read for sync. */
    suspend fun getRecent(limit: Int): List<BottleFeed>
    suspend fun getById(id: Long): BottleFeed?
    suspend fun getByClientId(clientId: String): BottleFeed?
    suspend fun insert(feed: BottleFeed): Long
    suspend fun insertWithBagConsume(feed: BottleFeed, consumedBagId: Long?, usedAt: Instant): Long
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
