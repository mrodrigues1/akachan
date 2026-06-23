package com.babytracker.domain.repository

import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface InventoryRepository {
    fun getActiveBags(): Flow<List<MilkBag>>
    fun getAllBags(): Flow<List<MilkBag>>
    fun getSummary(): Flow<InventorySummary>
    suspend fun currentSummary(): InventorySummary
    suspend fun getById(id: Long): MilkBag?

    /** Sums `volumeMl` across [ids] in one query. Missing ids contribute 0. [ids] must be non-empty. */
    suspend fun sumVolumeForIds(ids: List<Long>): Int
    suspend fun insert(bag: MilkBag): Long
    suspend fun update(bag: MilkBag)
    suspend fun updateDetails(
        id: Long,
        collectionDate: Instant,
        volumeMl: Int,
        notes: String?,
    ): Boolean
    suspend fun delete(bag: MilkBag)
}
