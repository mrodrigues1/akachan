package com.babytracker.domain.repository

import com.babytracker.domain.model.VaccineRecord
import kotlinx.coroutines.flow.Flow

interface VaccineRepository {
    fun observeAll(): Flow<List<VaccineRecord>>
    fun observeUpcoming(): Flow<List<VaccineRecord>>
    suspend fun getScheduledFutureAfter(nowMs: Long): List<VaccineRecord>
    suspend fun getAllOnce(): List<VaccineRecord>
    suspend fun getById(id: Long): VaccineRecord?
    suspend fun insert(record: VaccineRecord): Long
    suspend fun update(record: VaccineRecord)
    suspend fun deleteById(id: Long)
}
