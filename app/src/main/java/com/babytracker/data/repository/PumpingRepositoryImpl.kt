package com.babytracker.data.repository

import com.babytracker.data.local.dao.PumpingDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PumpingRepositoryImpl @Inject constructor(
    private val dao: PumpingDao,
) : PumpingRepository {

    override fun getAllSessions(): Flow<List<PumpingSession>> =
        dao.getAllSessions().map { rows -> rows.map { it.toDomain() } }

    override fun getActiveSession(): Flow<PumpingSession?> =
        dao.getActiveSession().map { it?.toDomain() }

    override suspend fun getById(id: Long): PumpingSession? = dao.getById(id)?.toDomain()

    override suspend fun insert(session: PumpingSession): Long = dao.insert(session.toEntity())

    override suspend fun update(session: PumpingSession) = dao.update(session.toEntity())

    override suspend fun delete(session: PumpingSession) = dao.delete(session.toEntity())
}
