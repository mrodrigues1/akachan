package com.babytracker.domain.repository

import com.babytracker.domain.model.SleepRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SleepRepository {
    fun getAllRecords(): Flow<List<SleepRecord>>

    /** Observes records with startTime >= [since], newest first. Bounded alternative to [getAllRecords]. */
    fun getRecordsSinceFlow(since: Instant): Flow<List<SleepRecord>>

    /**
     * Observes every record touching the window from [since] to now — started in it, ended in it
     * (a long/cross-midnight sleep whose start predates it), or still open regardless of age — newest
     * first. Bounded alternative to [getAllRecords] for screens that need the active session plus the
     * recent/last-completed records without re-mapping the whole history.
     */
    fun getRecentOrActiveRecordsFlow(since: Instant): Flow<List<SleepRecord>>
    fun observeLatestRecord(): Flow<SleepRecord?>

    /** Emits the single active (in-progress) session, or null once it ends, is edited to ended, or is deleted. */
    fun observeActiveRecord(): Flow<SleepRecord?>
    suspend fun getCompletedRecordsSince(since: Instant): List<SleepRecord>
    suspend fun getCompletedRecordsBetween(start: Instant, end: Instant): List<SleepRecord>
    suspend fun getRecentRecords(limit: Int): List<SleepRecord>
    suspend fun getLatestRecord(): SleepRecord?
    suspend fun getByClientId(clientId: String): SleepRecord?
    suspend fun getActiveRecord(): SleepRecord?
    suspend fun insertRecord(record: SleepRecord): Long
    suspend fun updateRecord(record: SleepRecord)
    suspend fun deleteRecord(id: Long)
    suspend fun startRecordIfNone(record: SleepRecord): Long?
    suspend fun stopActiveRecord(endTime: Instant): Boolean
}
