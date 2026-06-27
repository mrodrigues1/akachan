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
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRepositoryImpl @Inject constructor(
    private val dao: SleepDao
) : SleepRepository {
    override fun getAllRecords(): Flow<List<SleepRecord>> =
        dao.getAllRecords().mapList { it.toDomain() }

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

    override suspend fun getByClientId(clientId: String): SleepRecord? =
        dao.getByClientId(clientId)?.toDomain()

    // The single active (in-progress) session, newest first — used by partner START to converge
    // instead of inserting a second active record.
    override suspend fun getActiveRecord(): SleepRecord? =
        dao.getActiveRecordOnce()?.toDomain()

    override suspend fun insertRecord(record: SleepRecord): Long =
        try {
            dao.insertRecord(record.withClientId().toEntity())
        } catch (e: SQLiteConstraintException) {
            if (record.endTime == null) {
                dao.getActiveRecord()?.id ?: throw e
            } else {
                throw e
            }
        }

    // Identity is set once, at insert, and preserved across every edit: re-deriving clientId/startedBy
    // on update would corrupt the unique client_id index (two edited rows both '') and lose attribution.
    override suspend fun updateRecord(record: SleepRecord) {
        val existing = dao.getById(record.id)
        val toSave = if (existing != null) {
            record.toEntity().copy(clientId = existing.clientId, startedBy = existing.startedBy)
        } else {
            record.withClientId().toEntity()
        }
        dao.updateRecord(toSave)
    }

    override suspend fun deleteRecord(id: Long) =
        dao.deleteRecord(id)

    override suspend fun startRecordIfNone(record: SleepRecord): Long? =
        dao.startRecordIfNone(record.withClientId().toEntity())

    override suspend fun stopActiveRecord(endTime: Instant): Boolean =
        dao.stopActiveRecord(endTime.toEpochMilli())

    // Safety net for insert paths that don't mint their own (manual entry, tile, debug seed): a blank
    // clientId would violate the unique index on the second such row. Records that already carry one
    // (e.g. a partner-attributed session) are left untouched.
    private fun SleepRecord.withClientId(): SleepRecord =
        if (clientId.isBlank()) copy(clientId = UUID.randomUUID().toString()) else this
}
