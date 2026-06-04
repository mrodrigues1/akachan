package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import javax.inject.Inject

class DeleteBreastfeedingSessionUseCase @Inject constructor(
    private val repository: BreastfeedingRepository,
) {
    suspend operator fun invoke(session: BreastfeedingSession) {
        repository.deleteSession(session)
    }
}
