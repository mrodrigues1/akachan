package com.babytracker.domain.repository

import com.babytracker.domain.model.PumpingSession
import kotlinx.coroutines.flow.Flow

interface PumpingRepository {
    /** Observes the newest [limit] sessions, newest first. */
    fun getRecentSessionsFlow(limit: Int): Flow<List<PumpingSession>>
    fun getActiveSession(): Flow<PumpingSession?>
    suspend fun getActiveSessionOnce(): PumpingSession?
    suspend fun getById(id: Long): PumpingSession?
    suspend fun insert(session: PumpingSession): Long
    suspend fun update(session: PumpingSession)
    suspend fun updateEndTimeIfActive(session: PumpingSession): Boolean
    suspend fun delete(session: PumpingSession)
}
