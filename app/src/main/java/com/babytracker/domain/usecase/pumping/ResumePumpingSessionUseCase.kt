package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class ResumePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(session: PumpingSession) {
        require(session.isInProgress) { "Cannot resume a completed session" }
        val pausedAt = session.pausedAt ?: return
        val addedMs = (now().toEpochMilli() - pausedAt.toEpochMilli()).coerceAtLeast(0L)
        repository.update(
            session.copy(
                pausedAt = null,
                pausedDurationMs = session.pausedDurationMs + addedMs,
            )
        )
    }
}
