package com.babytracker.data.repository

import com.babytracker.data.local.dao.DiaperDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.repository.DiaperRepository
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiaperRepositoryImpl @Inject constructor(
    private val dao: DiaperDao,
) : DiaperRepository {
    override fun observeRecent(limit: Int): Flow<List<DiaperChange>> =
        dao.observeRecent(limit).mapList { it.toDomain() }

    override suspend fun getRecent(limit: Int): List<DiaperChange> =
        dao.getRecent(limit).map { it.toDomain() }

    override suspend fun getBetween(start: Instant, end: Instant): List<DiaperChange> =
        dao.getBetween(start.toEpochMilli(), end.toEpochMilli()).map { it.toDomain() }

    override suspend fun insert(change: DiaperChange): Long = dao.insert(change.toEntity())

    override suspend fun update(change: DiaperChange) = dao.update(change.toEntity())

    override suspend fun deleteById(id: Long) {
        dao.deleteById(id)
    }
}
