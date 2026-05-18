package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.repository.SleepRepository
import javax.inject.Inject

class DeleteSleepEntryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    suspend operator fun invoke(id: Long) = repository.deleteRecord(id)
}
