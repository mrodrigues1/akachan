package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import javax.inject.Inject

class UpdateSleepEntryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(id: Long, startTime: Instant, endTime: Instant, type: SleepType) {
        require(endTime > startTime) { "endTime must be after startTime" }
        repository.updateRecord(SleepRecord(id = id, startTime = startTime, endTime = endTime, sleepType = type))
    }
}
