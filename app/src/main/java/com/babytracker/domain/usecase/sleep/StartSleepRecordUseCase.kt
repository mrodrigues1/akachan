package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class StartSleepRecordUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(sleepType: SleepType): SleepRecord {
        val record = SleepRecord(
            startTime = Instant.now(),
            sleepType = sleepType,
            timezoneId = ZoneId.systemDefault().id
        )
        val id = repository.insertRecord(record)
        return record.copy(id = id)
    }
}
