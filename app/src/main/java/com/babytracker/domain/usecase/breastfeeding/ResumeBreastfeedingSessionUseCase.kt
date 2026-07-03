package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Clock
import java.time.Duration
import javax.inject.Inject

class ResumeBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository,
    private val clock: Clock,
) {
    /** Returns the persisted session, or [session] unchanged when it was not paused. */
    suspend operator fun invoke(session: BreastfeedingSession): BreastfeedingSession {
        val pausedAt = session.pausedAt ?: return session
        // Clamp so a backward clock adjustment can't corrupt the total with a negative delta.
        val additionalPause = Duration.between(pausedAt, clock.instant()).toMillis().coerceAtLeast(0L)
        val resumed = session.copy(
            pausedAt = null,
            pausedDurationMs = session.pausedDurationMs + additionalPause
        )
        repository.updateSession(resumed)
        return resumed
    }
}
