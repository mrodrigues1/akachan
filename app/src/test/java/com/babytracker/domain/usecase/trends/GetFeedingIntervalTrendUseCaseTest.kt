package com.babytracker.domain.usecase.trends

import com.babytracker.domain.trends.TrendRange
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GetFeedingIntervalTrendUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC)
    private val useCase = GetFeedingIntervalTrendUseCase(clock)

    @Test
    fun `averages same-day gaps in hours`() = runTest {
        // Feeds at 06:00, 09:00, 12:00 -> gaps 3h and 3h -> avg 3.0
        val feeds = TrendFeedInstants(
            breast = listOf(Instant.parse("2026-06-14T06:00:00Z"), Instant.parse("2026-06-14T09:00:00Z")),
            bottle = listOf(Instant.parse("2026-06-14T12:00:00Z")),
        )

        val day = useCase(TrendRange.SEVEN_DAYS, feeds).first { it.date.toString() == "2026-06-14" }
        assertEquals(3.0, day.averageHours!!, 0.001)
    }

    @Test
    fun `single feed day has null average`() = runTest {
        val feeds = TrendFeedInstants(breast = listOf(Instant.parse("2026-06-13T10:00:00Z")))

        val day = useCase(TrendRange.SEVEN_DAYS, feeds).first { it.date.toString() == "2026-06-13" }
        assertNull(day.averageHours)
    }
}
