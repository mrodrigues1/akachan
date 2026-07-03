package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class EditPartnerFeedUseCase @Inject constructor(
    private val submitFeedOp: SubmitFeedOpUseCase,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(
        entry: BottleFeedSnapshot,
        timestamp: Instant,
        volumeMl: Int,
        type: FeedType,
        notes: String?,
    ) {
        require(entry.author == FeedAuthor.PARTNER.name) { "Only partner-authored entries can be edited" }
        require(entry.clientId.isNotEmpty()) { "Entry has no clientId" }
        requireValidFeedInput(volumeMl, timestamp, now())
        requireValidNotes(notes)

        submitFeedOp { authorUid ->
            FeedOp(
                opId = UUID.randomUUID().toString(),
                action = FeedOpAction.UPDATE,
                entryClientId = entry.clientId,
                authorUid = authorUid,
                createdAtMs = now().toEpochMilli(),
                timestampMs = timestamp.toEpochMilli(),
                volumeMl = volumeMl,
                type = type.name,
                notes = notes,
            )
        }
    }
}
