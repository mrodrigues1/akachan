package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import javax.inject.Inject

class UpdateSleepEntryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(
        id: Long,
        startTime: Instant,
        endTime: Instant,
        type: SleepType,
        timezoneId: String? = null,
    ) {
        val now = Instant.now()
        val active = repository.getActiveRecord()
        val nearby = repository.getCompletedRecordsBetween(startTime, endTime)
        val existingRecords = if (active != null) nearby + active else nearby
        val error = validateSleepEntry(startTime, endTime, type, existingRecords, now, excludingId = id)
        require(error == null) { error?.name.orEmpty() }
        repository.updateRecord(
            SleepRecord(
                id = id,
                startTime = startTime,
                endTime = endTime,
                sleepType = type,
                timezoneId = timezoneId,
            ),
        )
    }
}
