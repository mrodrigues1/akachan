package com.babytracker.domain.repository

import com.babytracker.domain.model.DiaperChange
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface DiaperRepository {
    /** Observes the newest [limit] changes, newest first. */
    fun observeRecent(limit: Int): Flow<List<DiaperChange>>

    /** The [limit] most recent diaper changes, newest first. Bounded one-shot read for sync. */
    suspend fun getRecent(limit: Int): List<DiaperChange>
    suspend fun getBetween(start: Instant, end: Instant): List<DiaperChange>
    suspend fun insert(change: DiaperChange): Long
    suspend fun update(change: DiaperChange)
    suspend fun deleteById(id: Long)
}
