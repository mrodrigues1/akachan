package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class ResumeBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        val pausedAt = session.pausedAt ?: return
        // Clamp so a backward clock adjustment can't corrupt the total with a negative delta.
        val additionalPause = Duration.between(pausedAt, now()).toMillis().coerceAtLeast(0L)
        repository.updateSession(
            session.copy(
                pausedAt = null,
                pausedDurationMs = session.pausedDurationMs + additionalPause
            )
        )
    }
}
