package com.babytracker.domain.usecase.feeding

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class ObserveTodayFeedingSummaryUseCaseTest {

    private val zone = ZoneId.of("UTC")
    private val now = Instant.parse("2026-06-10T20:00:00Z")

    private val getBreastfeeding = mockk<BreastfeedingRepository>()
    private val observeBottles = mockk<BottleFeedRepository>()

    private fun useCase() =
        ObserveTodayFeedingSummaryUseCase(getBreastfeeding, observeBottles, zone) { now }

    @Test
    fun `sums today's feeds and ignores other days`() = runTest {
        val todayBottleA = BottleFeed(1, "client-1", Instant.parse("2026-06-10T08:00:00Z"), 120, FeedType.FORMULA, createdAt = Instant.EPOCH)
        val todayBottleB = BottleFeed(2, "client-2", Instant.parse("2026-06-10T12:00:00Z"), 90, FeedType.BREAST_MILK, createdAt = Instant.EPOCH)
        val yesterdayBottle = BottleFeed(3, "client-3", Instant.parse("2026-06-09T12:00:00Z"), 200, FeedType.FORMULA, createdAt = Instant.EPOCH)
        val todaySession = BreastfeedingSession(1, Instant.parse("2026-06-10T15:00:00Z"), Instant.parse("2026-06-10T15:10:00Z"), BreastSide.LEFT)
        val yesterdaySession = BreastfeedingSession(2, Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-09T15:10:00Z"), BreastSide.RIGHT)

        every { getBreastfeeding.getAllSessions() } returns flowOf(listOf(todaySession, yesterdaySession))
        every { observeBottles.getAll() } returns flowOf(listOf(todayBottleA, todayBottleB, yesterdayBottle))

        val summary = useCase()().first()

        assertEquals(210, summary.bottleVolumeMl)
        assertEquals(2, summary.bottleCount)
        assertEquals(1, summary.breastfeedingCount)
        assertEquals(3, summary.totalFeedCount)
        assertTrue(summary.hasAny)
    }

    @Test
    fun `emits empty summary when no feeds today`() = runTest {
        val yesterdayBottle = BottleFeed(1, "client-1", Instant.parse("2026-06-09T12:00:00Z"), 200, FeedType.FORMULA, createdAt = Instant.EPOCH)
        val yesterdaySession = BreastfeedingSession(1, Instant.parse("2026-06-09T15:00:00Z"), Instant.parse("2026-06-09T15:10:00Z"), BreastSide.RIGHT)

        every { getBreastfeeding.getAllSessions() } returns flowOf(listOf(yesterdaySession))
        every { observeBottles.getAll() } returns flowOf(listOf(yesterdayBottle))

        val summary = useCase()().first()

        assertEquals(0, summary.bottleVolumeMl)
        assertEquals(0, summary.totalFeedCount)
        assertFalse(summary.hasAny)
    }

    @Test
    fun `respects zone when bucketing into local day`() = runTest {
        // now = 2026-06-10T20:00Z. In America/New_York (EDT, UTC-4), local "today" is 2026-06-10.
        val zoneNy = ZoneId.of("America/New_York")
        // 2026-06-11T01:00Z is 2026-06-10 21:00 local -> still today in NY.
        val lateUtcStillTodayLocal = BottleFeed(1, "client-1", Instant.parse("2026-06-11T01:00:00Z"), 100, FeedType.FORMULA, createdAt = Instant.EPOCH)
        // 2026-06-10T03:00Z is 2026-06-09 23:00 local -> yesterday in NY.
        val earlyUtcYesterdayLocal = BottleFeed(2, "client-2", Instant.parse("2026-06-10T03:00:00Z"), 50, FeedType.FORMULA, createdAt = Instant.EPOCH)

        every { getBreastfeeding.getAllSessions() } returns flowOf(emptyList())
        every { observeBottles.getAll() } returns flowOf(listOf(lateUtcStillTodayLocal, earlyUtcYesterdayLocal))

        val summary = ObserveTodayFeedingSummaryUseCase(getBreastfeeding, observeBottles, zoneNy) { now }().first()

        assertEquals(100, summary.bottleVolumeMl)
        assertEquals(1, summary.bottleCount)
    }
}
