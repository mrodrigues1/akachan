package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import javax.inject.Inject

class DeleteDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
    suspend operator fun invoke(id: Long) {
        repository.deleteById(id)
        // Sync is best-effort: a partner-sync failure must never fail the local write.
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.DIAPERS) }
    }
}
