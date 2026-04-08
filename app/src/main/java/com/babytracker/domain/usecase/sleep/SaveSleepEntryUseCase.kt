package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import javax.inject.Inject

class SaveSleepEntryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        type: SleepType
    ): Long {
        require(endTime > startTime) { "endTime must be after startTime" }
        return repository.insertRecord(
            SleepRecord(
                startTime = startTime,
                endTime = endTime,
                sleepType = type
            )
        )
    }
}
