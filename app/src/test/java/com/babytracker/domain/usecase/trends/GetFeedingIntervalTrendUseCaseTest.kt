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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GetFeedingIntervalTrendUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var breastfeeding: BreastfeedingRepository
    private lateinit var bottle: BottleFeedRepository
    private lateinit var useCase: GetFeedingIntervalTrendUseCase

    private fun session(start: String) = BreastfeedingSession(
        startTime = Instant.parse(start),
        endTime = Instant.parse(start).plusSeconds(300),
        startingSide = BreastSide.LEFT,
    )

    private fun bottle(ts: String) = BottleFeed(
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
        useCase = GetFeedingIntervalTrendUseCase(breastfeeding, bottle, clock)
    }

    @Test
    fun `averages same-day gaps in hours`() = runTest {
        // Feeds at 06:00, 09:00, 12:00 -> gaps 3h and 3h -> avg 3.0
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-14T06:00:00Z"),
            session("2026-06-14T09:00:00Z"),
        )
        every { bottle.getSince(any()) } returns flowOf(listOf(bottle("2026-06-14T12:00:00Z")))

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }
        assertEquals(3.0, day.averageHours!!, 0.001)
    }

    @Test
    fun `single feed day has null average`() = runTest {
        coEvery { breastfeeding.getCompletedSessionsBetween(any(), any()) } returns listOf(
            session("2026-06-13T10:00:00Z"),
        )
        every { bottle.getSince(any()) } returns flowOf(emptyList())

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-13" }
        assertNull(day.averageHours)
    }
}
