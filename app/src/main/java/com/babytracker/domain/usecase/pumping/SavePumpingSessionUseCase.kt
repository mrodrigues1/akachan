package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class SavePumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
) {
    suspend operator fun invoke(
        startTime: Instant,
        endTime: Instant,
        breast: PumpingBreast,
        volumeMl: Int,
        notes: String?,
    ): PumpingSession {
        require(endTime.isAfter(startTime)) { "End must be after start" }
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
