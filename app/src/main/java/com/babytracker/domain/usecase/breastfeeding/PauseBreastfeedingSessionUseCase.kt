package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Clock
import javax.inject.Inject

class PauseBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository,
    private val clock: Clock,
) {
    /** Returns the persisted session, or [session] unchanged when it was already paused. */
    suspend operator fun invoke(session: BreastfeedingSession): BreastfeedingSession {
        if (session.isPaused) return session
        val paused = session.copy(pausedAt = clock.instant())
        repository.updateSession(paused)
        return paused
    }
}
