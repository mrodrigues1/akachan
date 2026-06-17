package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.repository.DiaperRepository
import java.time.Instant
import javax.inject.Inject

class EditDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(change: DiaperChange) {
        require(!change.timestamp.isAfter(now())) { "Diaper time cannot be in the future" }
        repository.update(change.copy(notes = change.notes?.takeIf { it.isNotBlank() }))
    }
}
