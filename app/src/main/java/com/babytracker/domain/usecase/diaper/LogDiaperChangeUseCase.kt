package com.babytracker.domain.usecase.diaper

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import java.time.Instant
import javax.inject.Inject

class LogDiaperChangeUseCase @Inject constructor(
    private val repository: DiaperRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        type: DiaperType,
        timestamp: Instant,
        notes: String? = null,
    ): Long {
        require(!timestamp.isAfter(now())) { "Diaper time cannot be in the future" }
        val id = repository.insert(
            DiaperChange(
                timestamp = timestamp,
                type = type,
                notes = notes?.takeIf { it.isNotBlank() },
                createdAt = now(),
            ),
        )
        // Sync is best-effort: a partner-sync failure must never fail the local write.
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.DIAPERS) }
        return id
    }
}
