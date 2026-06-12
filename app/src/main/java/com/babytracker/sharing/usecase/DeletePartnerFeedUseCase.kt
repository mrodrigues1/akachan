package com.babytracker.sharing.usecase

import com.babytracker.domain.model.FeedAuthor
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import com.babytracker.sharing.domain.model.FeedOp
import com.babytracker.sharing.domain.model.FeedOpAction
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class DeletePartnerFeedUseCase @Inject constructor(
    private val submitFeedOp: SubmitFeedOpUseCase,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(entry: BottleFeedSnapshot) {
        require(entry.author == FeedAuthor.PARTNER.name) { "Only partner-authored entries can be deleted" }
        require(entry.clientId.isNotEmpty()) { "Entry has no clientId" }

        submitFeedOp { authorUid ->
            FeedOp(
                opId = UUID.randomUUID().toString(),
                action = FeedOpAction.DELETE,
                entryClientId = entry.clientId,
                authorUid = authorUid,
                createdAtMs = now().toEpochMilli(),
            )
        }
    }
}
