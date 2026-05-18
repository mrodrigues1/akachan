package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import javax.inject.Inject

class DeleteMilkBagUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
) {
    suspend operator fun invoke(bag: MilkBag) {
        repository.delete(bag)
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }
    }
}
