package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class PausePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(session: PumpingSession) {
        require(session.isInProgress) { "Cannot pause a completed session" }
        if (session.isPaused) return
        repository.update(session.copy(pausedAt = now()))
    }
}
