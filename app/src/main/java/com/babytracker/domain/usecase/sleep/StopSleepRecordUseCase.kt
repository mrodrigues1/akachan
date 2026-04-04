package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import javax.inject.Inject

class StopSleepRecordUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(record: SleepRecord) {
        repository.updateRecord(record.copy(endTime = Instant.now()))
    }
}
