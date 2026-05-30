package com.babytracker.data.repository

import android.database.sqlite.SQLiteConstraintException
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

    override fun observeLatestRecord(): Flow<SleepRecord?> =
        dao.observeLatestRecord().map { it?.toDomain() }

    override suspend fun getCompletedRecordsSince(since: Instant): List<SleepRecord> =
        dao.getCompletedRecordsSince(since.toEpochMilli()).map { it.toDomain() }

    override suspend fun getCompletedRecordsBetween(start: Instant, end: Instant): List<SleepRecord> =
        dao.getCompletedRecordsBetween(start.toEpochMilli(), end.toEpochMilli()).map { it.toDomain() }

    override suspend fun getRecentRecords(limit: Int): List<SleepRecord> =
        dao.getRecentRecords(limit).map { it.toDomain() }

    override suspend fun getLatestRecord(): SleepRecord? =
        dao.getLatestRecord()?.toDomain()

    override suspend fun insertRecord(record: SleepRecord): Long =
        try {
            dao.insertRecord(record.toEntity())
        } catch (e: SQLiteConstraintException) {
            if (record.endTime == null) {
                dao.getActiveRecord()?.id ?: throw e
            } else {
                throw e
            }
        }

    override suspend fun updateRecord(record: SleepRecord) =
        dao.updateRecord(record.toEntity())

    override suspend fun deleteRecord(id: Long) =
        dao.deleteRecord(id)
}
