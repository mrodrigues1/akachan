package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.SleepRepository
import java.time.Instant
import javax.inject.Inject

class StopSleepRecordUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(sessionId: Long): SleepRecord? {
        val record = repository.getActiveRecord()?.takeIf { it.id == sessionId } ?: return null
        val stopped = record.copy(endTime = Instant.now())
        repository.updateRecord(stopped)
        return stopped
    }
}
