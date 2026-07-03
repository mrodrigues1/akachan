package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class SavePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        breast: PumpingBreast,
        volumeMl: Int,
        notes: String?,
    ): PumpingSession {
        require(endTime.isAfter(startTime)) { "End must be after start" }
        // end > start, so end <= now also bounds start.
        require(!endTime.isAfter(now())) { "End cannot be in the future" }
        require(volumeMl > 0) { "Volume must be greater than 0" }
        val session = PumpingSession(
            startTime = startTime,
            endTime = endTime,
            breast = breast,
            volumeMl = volumeMl,
            notes = notes,
        )
        val id = repository.insert(session)
        return session.copy(id = id)
    }
}
