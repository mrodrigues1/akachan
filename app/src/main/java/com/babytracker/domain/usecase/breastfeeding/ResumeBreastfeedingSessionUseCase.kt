package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class ResumeBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        val pausedAt = session.pausedAt ?: return
        val additionalPause = Duration.between(pausedAt, Instant.now()).toMillis()
        repository.updateSession(
            session.copy(
                pausedAt = null,
                pausedDurationMs = session.pausedDurationMs + additionalPause
            )
        )
    }
}
