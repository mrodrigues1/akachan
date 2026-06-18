package com.babytracker.data.repository

import com.babytracker.data.local.dao.VaccineDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.repository.VaccineRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VaccineRepositoryImpl @Inject constructor(
    private val dao: VaccineDao,
) : VaccineRepository {
    override fun observeAll(): Flow<List<VaccineRecord>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeUpcoming(): Flow<List<VaccineRecord>> =
        dao.observeUpcoming().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getScheduledFutureAfter(nowMs: Long): List<VaccineRecord> =
        dao.getScheduledFutureAfter(nowMs).map { it.toDomain() }

    override suspend fun getAllOnce(): List<VaccineRecord> = dao.getAllOnce().map { it.toDomain() }

    override suspend fun getById(id: Long): VaccineRecord? = dao.getById(id)?.toDomain()

    override suspend fun insert(record: VaccineRecord): Long = dao.insert(record.toEntity())

    override suspend fun update(record: VaccineRecord) = dao.update(record.toEntity())

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}
