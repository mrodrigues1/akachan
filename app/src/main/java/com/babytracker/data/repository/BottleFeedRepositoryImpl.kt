package com.babytracker.data.repository

import com.babytracker.data.local.dao.BottleFeedDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BottleFeedRepositoryImpl @Inject constructor(
    private val dao: BottleFeedDao,
) : BottleFeedRepository {

    override fun getRecentFlow(limit: Int): Flow<List<BottleFeed>> =
        dao.getRecentFlow(limit).mapList { it.toDomain() }

    override suspend fun getSinceOnce(start: Instant): List<BottleFeed> =
        dao.getSinceOnce(start.toEpochMilli()).map { it.toDomain() }

    override suspend fun getRecent(limit: Int): List<BottleFeed> =
        dao.getRecent(limit).map { it.toDomain() }

    override suspend fun getById(id: Long): BottleFeed? = dao.getById(id)?.toDomain()

    override suspend fun getByClientId(clientId: String): BottleFeed? =
        dao.getByClientId(clientId)?.toDomain()

    override suspend fun insert(feed: BottleFeed): Long = dao.insert(feed.toEntity())

    override suspend fun insertWithBagConsume(feed: BottleFeed, consumedBagId: Long?, usedAt: Instant): Long =
        dao.insertWithBagConsume(feed.toEntity(), consumedBagId, usedAt.toEpochMilli())

    override suspend fun updateDetails(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
    ): Boolean = dao.updateDetails(
        id = id,
        timestamp = timestamp.toEpochMilli(),
        volumeMl = volumeMl,
        type = type.name,
        linkedMilkBagId = linkedMilkBagId,
        notes = notes,
    ) > 0

    override suspend fun updateDetailsWithInventory(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
        usedAt: Instant,
    ): Boolean = dao.updateDetailsWithInventory(
        id = id,
        timestamp = timestamp.toEpochMilli(),
        volumeMl = volumeMl,
        type = type.name,
        linkedMilkBagId = linkedMilkBagId,
        notes = notes,
        usedAt = usedAt.toEpochMilli(),
    )

    override suspend fun deleteWithInventoryRestore(feed: BottleFeed): Boolean =
        dao.deleteWithInventoryRestore(feed.id)

    override suspend fun delete(feed: BottleFeed) = dao.delete(feed.toEntity())
}
