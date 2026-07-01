package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import java.time.Instant
import javax.inject.Inject

class MarkBagUsedUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncedWrite: SyncedWrite,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(bag: MilkBag) {
        if (bag.usedAt != null) return
        syncedWrite(SyncType.INVENTORY) {
            repository.update(bag.copy(usedAt = now()))
        }
    }
}
