package com.babytracker.domain.usecase.inventory

import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import java.time.Instant
import javax.inject.Inject

class UpdateMilkBagUseCase @Inject constructor(
    private val repository: InventoryRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        bagId: Long,
        collectionDate: Instant,
        volumeMl: Int,
        notes: String? = null,
    ): Boolean {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!collectionDate.isAfter(now())) { "Collection date cannot be in the future" }

        check(
            repository.updateDetails(
                id = bagId,
                collectionDate = collectionDate,
                volumeMl = volumeMl,
                notes = notes,
            ),
        ) { "Milk bag is no longer editable" }
        return runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }.isSuccess
    }
}
