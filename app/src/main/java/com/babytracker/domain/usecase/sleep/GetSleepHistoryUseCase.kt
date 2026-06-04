package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.repository.SleepRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetSleepHistoryUseCase @Inject constructor(
    private val repository: SleepRepository
) {
    operator fun invoke(): Flow<List<SleepRecord>> = repository.getAllRecords()
}
