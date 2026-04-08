package com.babytracker.domain.repository

import com.babytracker.domain.model.SleepRecord
import kotlinx.coroutines.flow.Flow

interface SleepRepository {
    fun getAllRecords(): Flow<List<SleepRecord>>
    fun getActiveRecord(): Flow<SleepRecord?>
    suspend fun insertRecord(record: SleepRecord): Long
    suspend fun updateRecord(record: SleepRecord)
}
