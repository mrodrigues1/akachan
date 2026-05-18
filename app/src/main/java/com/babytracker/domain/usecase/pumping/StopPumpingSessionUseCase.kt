package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class StopPumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(session: PumpingSession, volumeMl: Int?): PumpingSession {
        require(session.isInProgress) { "Cannot stop a completed session" }
        if (volumeMl != null) require(volumeMl > 0) { "Volume must be greater than 0" }
        val endInstant = now()
        val resolvedPausedMs = if (session.isPaused) {
            session.pausedDurationMs +
                (endInstant.toEpochMilli() - session.pausedAt!!.toEpochMilli()).coerceAtLeast(0L)
        } else {
            session.pausedDurationMs
        }
        val updated = session.copy(
            endTime = endInstant,
            volumeMl = volumeMl,
            pausedAt = null,
            pausedDurationMs = resolvedPausedMs,
        )
        repository.update(updated)
        return updated
    }
}
