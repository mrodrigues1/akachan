package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import javax.inject.Inject

class StartSleepRecordUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(type: SleepType): Long {
        val record = SleepRecord(
            startTime = Instant.now(),
            sleepType = type
        )
        return repository.insertRecord(record)
    }
}
