package com.babytracker.ui.partner

import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import com.babytracker.sharing.domain.model.BottleFeedSnapshot
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class PartnerFeedHistoryScreenTest {

    @Test
    fun `legacy empty-clientId feed keys are unique by index`() {
        val first = legacyFeed()
        val second = legacyFeed()

        assertNotEquals(
            partnerFeedHistoryItemKey(0, first),
            partnerFeedHistoryItemKey(1, second),
        )
    }

    private fun legacyFeed() = BottleFeedSnapshot(
        timestamp = 1_000L,
        volumeMl = 90,
        type = FeedType.FORMULA.name,
        clientId = "",
        author = FeedAuthor.OWNER.name,
        notes = null,
    )
}
