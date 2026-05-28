package com.babytracker.domain.repository

import com.babytracker.domain.model.SleepRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SleepRepository {
    fun getAllRecords(): Flow<List<SleepRecord>>
    fun observeLatestRecord(): Flow<SleepRecord?>
    suspend fun getCompletedRecordsSince(since: Instant): List<SleepRecord>
    suspend fun getCompletedRecordsBetween(start: Instant, end: Instant): List<SleepRecord>
    suspend fun getRecentRecords(limit: Int): List<SleepRecord>
    suspend fun getLatestRecord(): SleepRecord?
    suspend fun insertRecord(record: SleepRecord): Long
    suspend fun updateRecord(record: SleepRecord)
    suspend fun deleteRecord(id: Long)
}
