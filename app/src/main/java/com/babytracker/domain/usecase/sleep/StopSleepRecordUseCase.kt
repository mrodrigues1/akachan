package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.flow.first
import java.time.Instant
import javax.inject.Inject

class StopSleepRecordUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(sessionId: Long) {
        val record = repository.getAllRecords().first()
            .find { it.id == sessionId && it.isInProgress } ?: return
        repository.updateRecord(record.copy(endTime = Instant.now()))
    }
}
