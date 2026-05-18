package com.babytracker.domain.usecase.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.repository.PumpingRepository
import java.time.Instant
import javax.inject.Inject

class StartPumpingSessionUseCase @Inject constructor(
    private val repository: PumpingRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(breast: PumpingBreast): Long {
        val session = PumpingSession(startTime = now(), breast = breast)
        return repository.insert(session)
    }
}
