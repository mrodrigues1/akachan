package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedEntry
import com.babytracker.domain.model.FeedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class GroupFeedEntriesTest {

    private val zone = ZoneId.of("UTC")

    @Test
    fun `groups by day and sums bottle volume and counts`() {
        val day1Bottle = FeedEntry.Bottle(
            BottleFeed(
                id = 1L,
                clientId = "client-1",
                timestamp = Instant.parse("2026-06-01T08:00:00Z"),
                volumeMl = 120,
                type = FeedType.FORMULA,
                createdAt = Instant.EPOCH,
            ),
        )
        val day1BottleB = FeedEntry.Bottle(
            BottleFeed(
                id = 2L,
                clientId = "client-2",
                timestamp = Instant.parse("2026-06-01T12:00:00Z"),
                volumeMl = 90,
                type = FeedType.BREAST_MILK,
                createdAt = Instant.EPOCH,
            ),
        )
        val day1Breast = FeedEntry.Breastfeeding(
            BreastfeedingSession(
                id = 1L,
                startTime = Instant.parse("2026-06-01T15:00:00Z"),
                endTime = Instant.parse("2026-06-01T15:10:00Z"),
                startingSide = BreastSide.LEFT,
            ),
        )
        val day2Bottle = FeedEntry.Bottle(
            BottleFeed(
                id = 3L,
                clientId = "client-3",
                timestamp = Instant.parse("2026-06-02T09:00:00Z"),
                volumeMl = 150,
                type = FeedType.FORMULA,
                createdAt = Instant.EPOCH,
            ),
        )

        val groups = groupFeedEntriesByDay(listOf(day2Bottle, day1Breast, day1BottleB, day1Bottle), zone)

        assertEquals(2, groups.size)
        assertEquals(150, groups[0].totals.bottleVolumeMl)
        assertEquals(1, groups[0].totals.totalFeedCount)
        assertEquals(210, groups[1].totals.bottleVolumeMl)
        assertEquals(2, groups[1].totals.bottleCount)
        assertEquals(1, groups[1].totals.breastfeedingCount)
        assertEquals(3, groups[1].totals.totalFeedCount)
        assertEquals(Instant.parse("2026-06-01T15:00:00Z"), groups[1].entries.first().timestamp)
    }
}
