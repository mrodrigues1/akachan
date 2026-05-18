package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class UpdatePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
) {
    suspend operator fun invoke(
        original: PumpingSession,
        startTime: Instant,
        endTime: Instant?,
        breast: PumpingBreast,
        volumeMl: Int?,
        notes: String?,
    ): PumpingSession {
        if (endTime != null) require(endTime.isAfter(startTime)) { "End must be after start" }
        if (volumeMl != null) require(volumeMl > 0) { "Volume must be greater than 0" }
        val updated = original.copy(
            startTime = startTime,
            endTime = endTime,
            breast = breast,
            volumeMl = volumeMl,
            notes = notes,
        )
        repository.update(updated)
        return updated
    }
}
