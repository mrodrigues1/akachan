package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetPumpingHistoryUseCase @Inject constructor(
    private val repository: PumpingRepository,
) {
    operator fun invoke(): Flow<List<PumpingSession>> = repository.getAllSessions()
}
