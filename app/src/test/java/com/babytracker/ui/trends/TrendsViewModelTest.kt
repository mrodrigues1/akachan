package com.babytracker.ui.trends

import app.cash.turbine.test
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.usecase.trends.GetDayRhythmTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedVsSleepTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCase
import com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCase
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TrendsViewModelTest {
    private val frequency: GetFeedingFrequencyTrendUseCase = mockk()
    private val sleep: GetSleepDurationTrendUseCase = mockk()
    private val interval: GetFeedingIntervalTrendUseCase = mockk()
    private val feedVsSleep: GetFeedVsSleepTrendUseCase = mockk()
    private val rhythm: GetDayRhythmTrendUseCase = mockk()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(StandardTestDispatcher())
        coEvery { sleep(any()) } returns emptyList()
        coEvery { interval(any()) } returns emptyList()
        coEvery { feedVsSleep(any()) } returns emptyList()
        coEvery { rhythm(any()) } returns emptyList()
    }

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = TrendsViewModel(frequency, sleep, interval, feedVsSleep, rhythm)

    @Test
    fun `loads default 7-day range then settles loaded`() = runTest {
        coEvery { frequency(TrendRange.SEVEN_DAYS) } returns
            listOf(DailyFeedingCount(LocalDate.of(2026, 6, 14), 5))

        viewModel().uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem()
            assertEquals(TrendRange.SEVEN_DAYS, state.range)
            assertEquals(1, state.feedingFrequency.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onRangeSelected recomputes for new range`() = runTest {
        coEvery { frequency(any()) } returns emptyList()

        val vm = viewModel()
        vm.uiState.test {
            var state = awaitItem()
            while (state.isLoading) state = awaitItem() // settled on 7d
            assertEquals(TrendRange.SEVEN_DAYS, state.range)

            vm.onRangeSelected(TrendRange.THIRTY_DAYS)
            while (state.range != TrendRange.THIRTY_DAYS || state.isLoading) state = awaitItem()
            assertEquals(TrendRange.THIRTY_DAYS, state.range)
            assertFalse(state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
