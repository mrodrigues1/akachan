package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetBreastfeedingHistoryUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    operator fun invoke(): Flow<List<BreastfeedingSession>> = repository.getAllSessions()
}
