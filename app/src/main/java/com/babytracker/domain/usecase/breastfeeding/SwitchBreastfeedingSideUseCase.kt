package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import java.time.Clock
import javax.inject.Inject

class SwitchBreastfeedingSideUseCase @Inject constructor(
    private val repository: BreastfeedingRepository,
    private val clock: Clock,
) {
    /** Returns the persisted session, or [session] unchanged when it had already switched. */
    suspend operator fun invoke(session: BreastfeedingSession): BreastfeedingSession {
        if (session.switchTime != null) return session   // already switched once
        val switched = session.copy(switchTime = clock.instant())
        repository.updateSession(switched)
        return switched
    }
}
