package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import java.time.Instant
import javax.inject.Inject

class MarkBagUsedUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(bag: MilkBag) {
        if (bag.usedAt != null) return
        repository.update(bag.copy(usedAt = now()))
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }
    }
}
