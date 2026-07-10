package com.babytracker.ui.trends

import app.cash.turbine.test
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.usecase.trends.GetDayRhythmTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCase
import com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCase
import com.babytracker.domain.usecase.trends.LoadTrendFeedInstantsUseCase
import com.babytracker.domain.usecase.trends.TrendFeedInstants
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class TrendsViewModelTest {
    private val frequency: GetFeedingFrequencyTrendUseCase = mockk()
    private val sleep: GetSleepDurationTrendUseCase = mockk()
    private val interval: GetFeedingIntervalTrendUseCase = mockk()
    private val rhythm: GetDayRhythmTrendUseCase = mockk()
    private val loadFeeds: LoadTrendFeedInstantsUseCase = mockk()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    @BeforeEach
    fun setup() {
        coEvery { sleep(any()) } returns emptyList()
        coEvery { loadFeeds(any()) } returns TrendFeedInstants()
        coEvery { interval(any(), any()) } returns emptyList()
        coEvery { rhythm(any(), any()) } returns emptyList()
    }

    private fun viewModel() = TrendsViewModel(loadFeeds, frequency, sleep, interval, rhythm)

    @Test
    fun `loads default 7-day range then settles loaded`() = runTest {
        coEvery { frequency(TrendRange.SEVEN_DAYS, any()) } returns
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
        coEvery { frequency(any(), any()) } returns emptyList()

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
