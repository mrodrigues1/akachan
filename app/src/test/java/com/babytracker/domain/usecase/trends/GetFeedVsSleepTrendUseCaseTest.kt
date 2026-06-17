package com.babytracker.domain.usecase.trends

import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.TrendRange
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class GetFeedVsSleepTrendUseCaseTest {
    private lateinit var frequency: GetFeedingFrequencyTrendUseCase
    private lateinit var sleep: GetSleepDurationTrendUseCase
    private lateinit var useCase: GetFeedVsSleepTrendUseCase

    private val d1 = LocalDate.of(2026, 6, 13)
    private val d2 = LocalDate.of(2026, 6, 14)

    @BeforeEach
    fun setup() {
        frequency = mockk()
        sleep = mockk()
        useCase = GetFeedVsSleepTrendUseCase(frequency, sleep)
    }

    @Test
    fun `pairs feed count with sleep hours by day`() = runTest {
        coEvery { frequency(TrendRange.SEVEN_DAYS) } returns listOf(
            DailyFeedingCount(d1, 5),
            DailyFeedingCount(d2, 3),
        )
        coEvery { sleep(TrendRange.SEVEN_DAYS) } returns listOf(
            DailySleepDuration(d1, nightHours = 8.0, napHours = 2.0),
            DailySleepDuration(d2, nightHours = 6.0, napHours = 1.0),
        )

        val result = useCase(TrendRange.SEVEN_DAYS)

        assertEquals(2, result.size)
        assertEquals(d1, result[0].date)
        assertEquals(5, result[0].feedCount)
        assertEquals(10.0, result[0].sleepHours, 0.001)
        assertEquals(3, result[1].feedCount)
        assertEquals(7.0, result[1].sleepHours, 0.001)
    }

    @Test
    fun `feeds with no sleep that day read zero sleep hours`() = runTest {
        coEvery { frequency(TrendRange.SEVEN_DAYS) } returns listOf(DailyFeedingCount(d1, 4))
        coEvery { sleep(TrendRange.SEVEN_DAYS) } returns listOf(
            DailySleepDuration(d1, nightHours = 0.0, napHours = 0.0),
        )

        val result = useCase(TrendRange.SEVEN_DAYS)

        assertEquals(4, result[0].feedCount)
        assertEquals(0.0, result[0].sleepHours, 0.001)
    }

    @Test
    fun `empty inputs yield empty output`() = runTest {
        coEvery { frequency(TrendRange.SEVEN_DAYS) } returns emptyList()
        coEvery { sleep(TrendRange.SEVEN_DAYS) } returns emptyList()

        assertEquals(emptyList<Any>(), useCase(TrendRange.SEVEN_DAYS))
    }
}
