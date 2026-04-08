package com.babytracker.domain.repository

import com.babytracker.domain.model.BreastfeedingSession
import kotlinx.coroutines.flow.Flow

interface BreastfeedingRepository {
    fun getAllSessions(): Flow<List<BreastfeedingSession>>
    fun getActiveSession(): Flow<BreastfeedingSession?>
    suspend fun getLastSession(): BreastfeedingSession?
    suspend fun insertSession(session: BreastfeedingSession): Long
    suspend fun updateSession(session: BreastfeedingSession)
}
