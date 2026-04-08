package com.babytracker.data.repository

import com.babytracker.data.local.dao.BreastfeedingDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BreastfeedingRepositoryImpl @Inject constructor(
    private val dao: BreastfeedingDao
) : BreastfeedingRepository {
    override fun getAllSessions(): Flow<List<BreastfeedingSession>> =
        dao.getAllSessions().map { entities -> entities.map { it.toDomain() } }

    override fun getActiveSession(): Flow<BreastfeedingSession?> =
        dao.getActiveSession().map { it?.toDomain() }

    override suspend fun getLastSession(): BreastfeedingSession? =
        dao.getLastSession()?.toDomain()

    override suspend fun insertSession(session: BreastfeedingSession): Long =
        dao.insertSession(session.toEntity())

    override suspend fun updateSession(session: BreastfeedingSession) =
        dao.updateSession(session.toEntity())
}
