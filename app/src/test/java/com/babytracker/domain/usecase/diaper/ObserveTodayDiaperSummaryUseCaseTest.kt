package com.babytracker.domain.usecase.diaper

import app.cash.turbine.test
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

class ObserveTodayDiaperSummaryUseCaseTest {
    private val zone = ZoneId.of("UTC")
    private val now = ZonedDateTime.of(2026, 6, 16, 12, 0, 0, 0, zone).toInstant()

    @Test
    fun `counts only today and reports global last change`() = runTest {
        val observe = mockk<DiaperRepository>()
        val todayMorning = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()
        val todayLater = ZonedDateTime.of(2026, 6, 16, 11, 0, 0, 0, zone).toInstant()
        val yesterday = ZonedDateTime.of(2026, 6, 15, 23, 0, 0, 0, zone).toInstant()
        every { observe.observeRecent(any()) } returns flowOf(
            // observeRecent is DESC; provide in that order
            listOf(
                DiaperChange(id = 3, timestamp = todayLater, type = DiaperType.WET, createdAt = todayLater),
                DiaperChange(id = 2, timestamp = todayMorning, type = DiaperType.DIRTY, createdAt = todayMorning),
                DiaperChange(id = 1, timestamp = yesterday, type = DiaperType.BOTH, createdAt = yesterday),
            ),
        )
        val useCase = ObserveTodayDiaperSummaryUseCase(observe, zone) { now }

        useCase().test {
            val summary = awaitItem()
            assertEquals(2, summary.count)
            assertEquals(todayLater, summary.lastChangeAt)
            awaitComplete()
        }
    }

    @Test
    fun `empty history yields zero count and null last change`() = runTest {
        val observe = mockk<DiaperRepository>()
        every { observe.observeRecent(any()) } returns flowOf(emptyList<DiaperChange>())
        ObserveTodayDiaperSummaryUseCase(observe, zone) { now }().test {
            val summary = awaitItem()
            assertEquals(0, summary.count)
            assertEquals(null, summary.lastChangeAt)
            awaitComplete()
        }
    }
}
