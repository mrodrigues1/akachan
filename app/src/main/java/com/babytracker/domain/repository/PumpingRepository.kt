package com.babytracker.domain.repository

import com.babytracker.domain.model.PumpingSession
import kotlinx.coroutines.flow.Flow

interface PumpingRepository {
    fun getAllSessions(): Flow<List<PumpingSession>>
    fun getActiveSession(): Flow<PumpingSession?>
    suspend fun getActiveSessionOnce(): PumpingSession?
    suspend fun getById(id: Long): PumpingSession?
    suspend fun insert(session: PumpingSession): Long
    suspend fun update(session: PumpingSession)
    suspend fun updateEndTimeIfActive(session: PumpingSession): Boolean
    suspend fun delete(session: PumpingSession)
}
