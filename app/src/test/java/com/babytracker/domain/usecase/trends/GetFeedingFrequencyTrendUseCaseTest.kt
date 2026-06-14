package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedType
import com.babytracker.domain.repository.BottleFeedRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.trends.TrendRange
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class GetFeedingFrequencyTrendUseCaseTest {
    private val zone = ZoneOffset.UTC

    // Fixed "now" = 2026-06-14T12:00:00Z
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), zone)
    private lateinit var breastfeeding: BreastfeedingRepository
    private lateinit var bottle: BottleFeedRepository
    private lateinit var useCase: GetFeedingFrequencyTrendUseCase

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

    @BeforeEach
    fun setup() {
        breastfeeding = mockk()
        bottle = mockk()
        useCase = GetFeedingFrequencyTrendUseCase(breastfeeding, bottle, clock)
    }

    @Test
    fun `counts breastfeeding and bottle feeds per day with zero-fill`() = runTest {
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-14T08:00:00Z"),
            session("2026-06-14T11:00:00Z"),
        )
        every { bottle.getSince(any()) } returns flowOf(
            listOf(bottle("2026-06-13T09:00:00Z")),
        )

        val result = useCase(TrendRange.SEVEN_DAYS)

        assertEquals(7, result.size)
        assertEquals(2, result.first { it.date.toString() == "2026-06-14" }.count)
        assertEquals(1, result.first { it.date.toString() == "2026-06-13" }.count)
        assertEquals(0, result.first { it.date.toString() == "2026-06-12" }.count)
    }

    @Test
    fun `empty data yields all zero days`() = runTest {
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns emptyList()
        every { bottle.getSince(any()) } returns flowOf(emptyList())

        val result = useCase(TrendRange.SEVEN_DAYS)

        assertEquals(7, result.size)
        assertEquals(0, result.sumOf { it.count })
    }

    @Test
    fun `buckets near-midnight feeds by the clock's local day`() = runTest {
        // Zone UTC-3 (Sao Paulo, no DST in 2026). 2026-06-14T02:00Z == 2026-06-13 23:00 local.
        val saoPauloZone = ZoneId.of("America/Sao_Paulo")
        val saoPauloClock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), saoPauloZone)
        val zonedUseCase = GetFeedingFrequencyTrendUseCase(breastfeeding, bottle, saoPauloClock)
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-14T02:00:00Z"), // 2026-06-13 23:00 local
        )
        every { bottle.getSince(any()) } returns flowOf(emptyList())

        val result = zonedUseCase(TrendRange.SEVEN_DAYS)

        assertEquals(1, result.first { it.date.toString() == "2026-06-13" }.count)
        assertEquals(0, result.first { it.date.toString() == "2026-06-14" }.count)
    }
}
