package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import javax.inject.Inject

class DeleteDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val syncedWrite: SyncedWrite,
) {
    suspend operator fun invoke(id: Long) {
        syncedWrite(SyncType.DIAPERS) {
            repository.deleteById(id)
        }
    }
}
