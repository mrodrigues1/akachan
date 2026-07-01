package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import javax.inject.Inject

class DeleteMilkBagUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncedWrite: SyncedWrite,
) {
    suspend operator fun invoke(bag: MilkBag) {
        syncedWrite(SyncType.INVENTORY) {
            repository.delete(bag)
        }
    }
}
