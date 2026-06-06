package com.babytracker.domain.usecase.baby

import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.repository.BabyEventRepository
import java.time.Clock
import java.time.Instant
import javax.inject.Inject

class LogBabyEventUseCase @Inject constructor(
    private val repository: BabyEventRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(type: BabyEventType) {
        val now = Instant.now(clock)
        repository.logEvent(
            BabyEvent(
                timestamp = now,
                type = type,
                createdAt = now,
            )
        )
    }
}
