package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import java.time.Instant
import javax.inject.Inject

class EditBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        id: Long,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedMilkBagId: Long?,
        notes: String?,
    ) {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!timestamp.isAfter(now())) { "Feed time cannot be in the future" }

        check(
            repository.updateDetailsWithInventory(
                id = id,
                timestamp = timestamp,
                volumeMl = volumeMl,
                type = type,
                linkedMilkBagId = linkedMilkBagId,
                notes = notes,
                usedAt = now(),
            ),
        ) { "Bottle feed no longer exists" }

        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.INVENTORY) }
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }
    }
}
