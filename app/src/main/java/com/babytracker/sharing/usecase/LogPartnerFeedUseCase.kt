package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedType
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import com.babytracker.sharing.domain.model.MilkBagSnapshot
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class LogPartnerFeedUseCase @Inject constructor(
    private val submitFeedOp: SubmitFeedOpUseCase,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        selectedBag: MilkBagSnapshot?,
        notes: String?,
    ): String {
        requireValidFeedInput(volumeMl, timestamp, now())

        val entryClientId = UUID.randomUUID().toString()
        submitFeedOp { authorUid ->
            FeedOp(
                opId = UUID.randomUUID().toString(),
                action = FeedOpAction.CREATE,
                entryClientId = entryClientId,
                authorUid = authorUid,
                createdAtMs = now().toEpochMilli(),
                timestampMs = timestamp.toEpochMilli(),
                volumeMl = volumeMl,
                type = type.name,
                notes = notes,
                consumedBagId = selectedBag?.id,
            )
        }
        return entryClientId
    }
}
