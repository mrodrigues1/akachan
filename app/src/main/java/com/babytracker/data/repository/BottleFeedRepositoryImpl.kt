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

    override fun getAll(): Flow<List<BottleFeed>> =
        dao.getAll().map { rows -> rows.map { it.toDomain() } }

    override fun getSince(start: Instant): Flow<List<BottleFeed>> =
        dao.getSince(start.toEpochMilli()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun insert(feed: BottleFeed): Long = dao.insert(feed.toEntity())

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

    override suspend fun delete(feed: BottleFeed) = dao.delete(feed.toEntity())
}
