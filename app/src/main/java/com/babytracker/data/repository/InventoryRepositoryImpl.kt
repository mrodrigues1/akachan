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
        dao.getActiveBags().map { rows -> rows.map { it.toDomain() } }

    override fun getAllBags(): Flow<List<MilkBag>> =
        dao.getAllBags().map { rows -> rows.map { it.toDomain() } }

    override fun getSummary(): Flow<InventorySummary> =
        dao.getActiveSummary().map { row ->
            InventorySummary(
                totalMl = row.totalMl,
                bagCount = row.bagCount,
                oldestBagDate = row.oldestBagDateMs?.let { Instant.ofEpochMilli(it) },
            )
        }

    override suspend fun currentSummary(): InventorySummary = getSummary().first()

    override suspend fun insert(bag: MilkBag): Long = dao.insert(bag.toEntity())

    override suspend fun update(bag: MilkBag) = dao.update(bag.toEntity())

    override suspend fun delete(bag: MilkBag) = dao.delete(bag.toEntity())
}
