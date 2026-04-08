package com.babytracker.data.repository

import com.babytracker.data.local.dao.SleepDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl @Inject constructor(
    private val dao: SleepDao
) : SleepRepository {
    override fun getAllRecords(): Flow<List<SleepRecord>> =
        dao.getAllRecords().map { entities -> entities.map { it.toDomain() } }

    override fun getActiveRecord(): Flow<SleepRecord?> =
        dao.getActiveRecord().map { it?.toDomain() }

    override suspend fun getCompletedRecordsSince(since: Instant): List<SleepRecord> =
        dao.getCompletedRecordsSince(since.toEpochMilli()).map { it.toDomain() }

    override suspend fun insertRecord(record: SleepRecord): Long =
        dao.insertRecord(record.toEntity())

    override suspend fun updateRecord(record: SleepRecord) =
        dao.updateRecord(record.toEntity())
}
