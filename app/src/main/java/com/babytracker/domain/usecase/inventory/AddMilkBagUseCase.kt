package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase.SyncType
import com.babytracker.sharing.usecase.SyncedWrite
import java.time.Instant
import javax.inject.Inject

class AddMilkBagUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncedWrite: SyncedWrite,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        collectionDate: Instant,
        volumeMl: Int,
        sourceSessionId: Long? = null,
        notes: String? = null,
    ): Long {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        val bag = MilkBag(
            collectionDate = collectionDate,
            volumeMl = volumeMl,
            sourceSessionId = sourceSessionId,
            notes = notes,
            createdAt = now(),
        )
        return syncedWrite(SyncType.INVENTORY) { repository.insert(bag) }
    }
}
