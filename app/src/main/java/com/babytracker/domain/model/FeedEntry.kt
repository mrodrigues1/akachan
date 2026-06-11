package com.babytracker.domain.model

import java.time.Instant
import java.time.LocalDate

sealed interface FeedEntry {
    val timestamp: Instant

    data class Bottle(val feed: BottleFeed) : FeedEntry {
        override val timestamp: Instant
            get() = feed.timestamp
    }

    data class Breastfeeding(val session: BreastfeedingSession) : FeedEntry {
        override val timestamp: Instant
            get() = session.startTime
    }
}

data class DailyFeedingTotals(
    val bottleVolumeMl: Int,
    val bottleCount: Int,
    val breastfeedingCount: Int,
) {
    val totalFeedCount: Int
        get() = bottleCount + breastfeedingCount
}

data class FeedingDayGroup(
    val date: LocalDate,
    val totals: DailyFeedingTotals,
    val entries: List<FeedEntry>,
)
