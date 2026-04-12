package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Instant
import javax.inject.Inject

class PauseBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        if (session.isPaused) return
        repository.updateSession(session.copy(pausedAt = Instant.now()))
    }
}
