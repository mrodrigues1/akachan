package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import java.time.Instant
import javax.inject.Inject

class LogDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val syncedWrite: SyncedWrite,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        type: DiaperType,
        timestamp: Instant,
        notes: String? = null,
    ): Long {
        require(!timestamp.isAfter(now())) { "Diaper time cannot be in the future" }
        return syncedWrite(SyncType.DIAPERS) {
            repository.insert(
                DiaperChange(
                    timestamp = timestamp,
                    type = type,
                    notes = notes?.takeIf { it.isNotBlank() },
                    createdAt = now(),
                ),
            )
        }
    }
}
