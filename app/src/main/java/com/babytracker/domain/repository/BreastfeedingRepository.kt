package com.babytracker.domain.repository

import com.babytracker.domain.model.BreastfeedingSession
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface BreastfeedingRepository {
    /** Observes the [limit] most recent sessions, newest first. */
    fun getRecentSessionsFlow(limit: Int): Flow<List<BreastfeedingSession>>
    fun observeLatestSession(): Flow<BreastfeedingSession?>

    /** Observes the most recently ended completed session, or null if none exist. */
    fun observeLatestCompletedSession(): Flow<BreastfeedingSession?>
    fun getActiveSession(): Flow<BreastfeedingSession?>
    suspend fun getLastSession(): BreastfeedingSession?
    suspend fun getRecentSessions(limit: Int): List<BreastfeedingSession>
    suspend fun getCompletedSessionsBetween(start: Instant, end: Instant): List<BreastfeedingSession>
    suspend fun insertSession(session: BreastfeedingSession): Long
    suspend fun updateSession(session: BreastfeedingSession)
    suspend fun deleteSession(session: BreastfeedingSession)
    suspend fun startSessionIfNone(session: BreastfeedingSession): Long?

    /**
     * Atomically ends the active session: re-reads the active row inside a transaction and folds
     * any open pause into its paused duration. Returns false when no session is active.
     */
    suspend fun stopActiveSession(endTime: Instant): Boolean
}
