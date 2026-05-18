package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import javax.inject.Inject

class DeletePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
) {
    suspend operator fun invoke(session: PumpingSession) = repository.delete(session)
}
