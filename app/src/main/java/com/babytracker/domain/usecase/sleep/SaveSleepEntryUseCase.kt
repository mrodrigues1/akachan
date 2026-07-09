package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

class SaveSleepEntryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        type: SleepType
    ): Long {
        val now = Instant.now()
        val active = repository.getActiveRecord()
        val nearby = repository.getCompletedRecordsBetween(startTime, endTime)
        val existingRecords = if (active != null) nearby + active else nearby
        val error = validateSleepEntry(startTime, endTime, type, existingRecords, now)
        require(error == null) { error?.name.orEmpty() }
        return repository.insertRecord(
            SleepRecord(
                startTime = startTime,
                endTime = endTime,
                sleepType = type,
                timezoneId = ZoneId.systemDefault().id,
            )
        )
    }
}
