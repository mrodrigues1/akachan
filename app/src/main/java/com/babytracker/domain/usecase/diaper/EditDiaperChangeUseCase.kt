package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import java.time.Instant
import javax.inject.Inject

class EditDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val syncedWrite: SyncedWrite,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(change: DiaperChange) {
        require(!change.timestamp.isAfter(now())) { "Diaper time cannot be in the future" }
        syncedWrite(SyncType.DIAPERS) {
            repository.update(change.copy(notes = change.notes?.takeIf { it.isNotBlank() }))
        }
    }
}
