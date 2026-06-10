package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Instant
import javax.inject.Inject

class SaveBreastfeedingEntryUseCase @Inject constructor(
    private val repository: BreastfeedingRepository
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        startingSide: BreastSide
    ): Long {
        val session = BreastfeedingSession(
            startTime = startTime,
            endTime = endTime,
            startingSide = startingSide
        )
        return repository.insertSession(session)
    }
}
