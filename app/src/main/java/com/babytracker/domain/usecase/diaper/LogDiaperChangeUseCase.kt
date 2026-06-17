package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import java.time.Instant
import javax.inject.Inject

class LogDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        type: DiaperType,
        timestamp: Instant,
        notes: String? = null,
    ): Long {
        require(!timestamp.isAfter(now())) { "Diaper time cannot be in the future" }
        return repository.insert(
            DiaperChange(
                timestamp = timestamp,
                type = type,
                notes = notes?.takeIf { it.isNotBlank() },
                createdAt = now(),
            ),
        )
    }
}
