package com.babytracker.domain.repository

import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.MilkBag
import kotlinx.coroutines.flow.Flow

interface InventoryRepository {
    fun getActiveBags(): Flow<List<MilkBag>>
    fun getAllBags(): Flow<List<MilkBag>>
    fun getSummary(): Flow<InventorySummary>
    suspend fun currentSummary(): InventorySummary
    suspend fun insert(bag: MilkBag): Long
    suspend fun update(bag: MilkBag)
    suspend fun delete(bag: MilkBag)
}
