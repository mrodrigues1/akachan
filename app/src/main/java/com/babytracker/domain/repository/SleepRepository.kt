package com.babytracker.domain.repository

import com.babytracker.domain.model.SleepRecord
import kotlinx.coroutines.flow.Flow
import java.time.Instant

interface SleepRepository {
    fun getAllRecords(): Flow<List<SleepRecord>>
    suspend fun getCompletedRecordsSince(since: Instant): List<SleepRecord>
    suspend fun getRecentRecords(limit: Int): List<SleepRecord>
    suspend fun insertRecord(record: SleepRecord): Long
    suspend fun updateRecord(record: SleepRecord)
}
