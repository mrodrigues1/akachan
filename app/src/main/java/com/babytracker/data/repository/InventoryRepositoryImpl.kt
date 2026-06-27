package com.babytracker.data.repository

import com.babytracker.data.local.dao.MilkBagDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val dao: MilkBagDao,
) : InventoryRepository {

    override fun getActiveBags(): Flow<List<MilkBag>> =
        dao.getActiveBags().mapList { it.toDomain() }

    override fun getAllBags(): Flow<List<MilkBag>> =
        dao.getAllBags().mapList { it.toDomain() }

    override fun getSummary(): Flow<InventorySummary> =
        dao.getActiveSummary().map { row ->
            InventorySummary(
                totalMl = row.totalMl,
                bagCount = row.bagCount,
                oldestBagDate = row.oldestBagDateMs?.let { Instant.ofEpochMilli(it) },
            )
        }

    override suspend fun currentSummary(): InventorySummary = getSummary().first()

    override suspend fun getById(id: Long): MilkBag? = dao.getById(id)?.toDomain()

    override suspend fun sumVolumeForIds(ids: List<Long>): Int = dao.sumVolumeForIds(ids)

    override suspend fun insert(bag: MilkBag): Long = dao.insert(bag.toEntity())

    override suspend fun update(bag: MilkBag) = dao.update(bag.toEntity())

    override suspend fun updateDetails(
        id: Long,
        collectionDate: Instant,
        volumeMl: Int,
        notes: String?,
    ): Boolean = dao.updateActiveDetails(
        id = id,
        collectionDate = collectionDate.toEpochMilli(),
        volumeMl = volumeMl,
        notes = notes,
    ) > 0

    override suspend fun delete(bag: MilkBag) = dao.delete(bag.toEntity())
}
