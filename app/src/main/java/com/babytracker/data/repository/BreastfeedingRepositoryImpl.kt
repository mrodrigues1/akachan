package com.babytracker.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreastfeedingRepositoryImpl @Inject constructor(
    private val dao: BreastfeedingDao
) : BreastfeedingRepository {
    override fun getAllSessions(): Flow<List<BreastfeedingSession>> =
        dao.getAllSessions().map { entities -> entities.map { it.toDomain() } }

    override fun observeLatestSession(): Flow<BreastfeedingSession?> =
        dao.observeLatestSession().map { it?.toDomain() }

    override fun getActiveSession(): Flow<BreastfeedingSession?> =
        dao.getActiveSession().map { it?.toDomain() }

    override suspend fun getLastSession(): BreastfeedingSession? =
        dao.getLastSession()?.toDomain()

    override suspend fun getRecentSessions(limit: Int): List<BreastfeedingSession> =
        dao.getRecentSessions(limit).map { it.toDomain() }

    override suspend fun getCompletedSessionsBetween(
        start: Instant,
        end: Instant,
    ): List<BreastfeedingSession> =
        dao.getCompletedSessionsBetween(start.toEpochMilli(), end.toEpochMilli()).map { it.toDomain() }

    override suspend fun insertSession(session: BreastfeedingSession): Long =
        try {
            dao.insertSession(session.toEntity())
        } catch (e: SQLiteConstraintException) {
            if (session.endTime == null) {
                dao.getActiveSessionOnce()?.id ?: throw e
            } else {
                throw e
            }
        }

    override suspend fun updateSession(session: BreastfeedingSession) =
        dao.updateSession(session.toEntity())

    override suspend fun deleteSession(session: BreastfeedingSession) =
        dao.deleteSession(session.toEntity())
}
