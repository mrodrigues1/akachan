package com.babytracker.domain.usecase.bottlefeed

import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import java.time.Instant
import javax.inject.Inject

class EditBottleFeedUseCase @Inject constructor(
    private val repository: BottleFeedRepository,
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
            repository.updateDetails(
                id = id,
                timestamp = timestamp,
                volumeMl = volumeMl,
                type = type,
                linkedMilkBagId = linkedMilkBagId,
                notes = notes,
            ),
        ) { "Bottle feed no longer exists" }
    }
}
