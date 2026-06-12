package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.usecase.inventory.MarkBagUsedUseCase
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class LogBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
    private val markBagUsed: MarkBagUsedUseCase,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val now: () -> Instant,
) {
    suspend operator fun invoke(
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        linkedBag: MilkBag?,
        notes: String?,
    ): Long {
        require(volumeMl > 0) { "Volume must be greater than 0" }
        require(!timestamp.isAfter(now())) { "Feed time cannot be in the future" }

        val id = repository.insert(
            BottleFeed(
                clientId = UUID.randomUUID().toString(),
                timestamp = timestamp,
                volumeMl = volumeMl,
                type = type,
                linkedMilkBagId = linkedBag?.id,
                notes = notes,
                createdAt = now(),
            ),
        )
        if (linkedBag != null) {
            markBagUsed(linkedBag)
        }
        runCatching { syncToFirestore(SyncToFirestoreUseCase.SyncType.BOTTLE_FEEDS) }
        return id
    }
}
