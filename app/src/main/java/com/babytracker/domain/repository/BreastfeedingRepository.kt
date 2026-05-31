package com.babytracker.domain.repository

import com.babytracker.domain.model.BreastfeedingSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BreastfeedingRepository {
    fun getAllSessions(): Flow<List<BreastfeedingSession>>
    fun observeLatestSession(): Flow<BreastfeedingSession?>
    fun getActiveSession(): Flow<BreastfeedingSession?>
    suspend fun getLastSession(): BreastfeedingSession?
    suspend fun getRecentSessions(limit: Int): List<BreastfeedingSession>
    suspend fun getCompletedSessionsBetween(start: Instant, end: Instant): List<BreastfeedingSession>
    suspend fun insertSession(session: BreastfeedingSession): Long
    suspend fun updateSession(session: BreastfeedingSession)
    suspend fun deleteSession(session: BreastfeedingSession)
    suspend fun startSessionIfNone(session: BreastfeedingSession): Long?
    suspend fun stopActiveSession(endTime: Instant): Boolean
}
