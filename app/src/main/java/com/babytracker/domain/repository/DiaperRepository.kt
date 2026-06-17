package com.babytracker.domain.repository

import com.babytracker.domain.model.DiaperChange
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface DiaperRepository {
    fun observeAll(): Flow<List<DiaperChange>>
    fun observeLatest(): Flow<DiaperChange?>
    suspend fun getBetween(start: Instant, end: Instant): List<DiaperChange>
    suspend fun getById(id: Long): DiaperChange?
    suspend fun insert(change: DiaperChange): Long
    suspend fun update(change: DiaperChange)
    suspend fun deleteById(id: Long)
}
