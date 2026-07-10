package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.TrendRange
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class LoadTrendFeedInstantsUseCaseTest {
    // Fixed "now" = 2026-06-14T12:00:00Z
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC)

    private fun session(start: String) = BreastfeedingSession(
        id = 0,
        startTime = Instant.parse(start),
        endTime = Instant.parse(start).plusSeconds(600),
        startingSide = BreastSide.LEFT,
    )

    private fun bottle(ts: String) = BottleFeed(
        id = 0,
        clientId = ts,
        timestamp = Instant.parse(ts),
        volumeMl = 100,
        type = FeedType.FORMULA,
        createdAt = Instant.parse(ts),
    )

    @Test
    fun `loads breast starts and bottle timestamps, dropping future-dated bottles`() = runTest {
        val breastfeeding = mockk<BreastfeedingRepository>()
        val bottles = mockk<BottleFeedRepository>()
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-14T08:00:00Z"),
        )
        coEvery { bottles.getSinceOnce(any()) } returns listOf(
            bottle("2026-06-14T09:00:00Z"),
            // Future-dated relative to the fixed clock — must be dropped to match the session cap.
            bottle("2026-06-14T13:00:00Z"),
        )

        val feeds = LoadTrendFeedInstantsUseCase(breastfeeding, bottles, clock)(TrendRange.SEVEN_DAYS)

        assertEquals(listOf(Instant.parse("2026-06-14T08:00:00Z")), feeds.breast)
        assertEquals(listOf(Instant.parse("2026-06-14T09:00:00Z")), feeds.bottle)
        assertEquals(2, feeds.all.size)
    }
}
