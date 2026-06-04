package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Instant
import javax.inject.Inject

class StopBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        repository.updateSession(session.copy(endTime = Instant.now()))
    }
}
