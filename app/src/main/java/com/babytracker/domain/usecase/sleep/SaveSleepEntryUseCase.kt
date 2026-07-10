package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class SaveSleepEntryUseCase @Inject constructor(
    private val repository: SleepRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        type: SleepType
    ): Long {
        val now = clock.instant()
        // Single transaction: without it two concurrent saves can each pass validation against
        // the same pre-write snapshot and persist overlapping records (#775).
        return repository.inTransaction {
            val active = repository.getActiveRecord()
            val nearby = repository.getCompletedRecordsBetween(startTime, endTime)
            val existingRecords = if (active != null) nearby + active else nearby
            val error = validateSleepEntry(startTime, endTime, type, existingRecords, now)
            require(error == null) { error?.name.orEmpty() }
            repository.insertRecord(
                SleepRecord(
                    startTime = startTime,
                    endTime = endTime,
                    sleepType = type,
                    timezoneId = clock.zone.id,
                )
            )
        }
    }
}
