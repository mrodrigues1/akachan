package com.babytracker.ui.trends

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCase
import com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCase
import com.babytracker.ui.theme.BabyTrackerTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test

class TrendsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val frequency = mockk<GetFeedingFrequencyTrendUseCase>()
    private val sleep = mockk<GetSleepDurationTrendUseCase>()
    private val interval = mockk<GetFeedingIntervalTrendUseCase>()

    private fun viewModel(): TrendsViewModel {
        coEvery { frequency(any()) } returns emptyList()
        coEvery { sleep(any()) } returns emptyList()
        coEvery { interval(any()) } returns emptyList()
        return TrendsViewModel(frequency, sleep, interval)
    }

    private fun setScreen(vm: TrendsViewModel) {
        composeRule.setContent {
            BabyTrackerTheme(themeConfig = ThemeConfig.LIGHT) {
                TrendsScreen(onNavigateBack = {}, viewModel = vm)
            }
        }
    }

    @Test
    fun rangeSelectorAndEmptyStatesRender() {
        setScreen(viewModel())

        composeRule.onNodeWithTag("trends_range_7").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_range_14").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_range_30").assertIsDisplayed()

        // With no data every chart shows its empty placeholder rather than a chart.
        composeRule.onNodeWithTag("trends_feeding_chart_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_sleep_chart_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_interval_chart_empty").assertIsDisplayed()
    }

    @Test
    fun selectingRangeRecomputes() {
        setScreen(viewModel())

        composeRule.onNodeWithTag("trends_range_30").performClick()
        composeRule.waitForIdle()

        coVerify(timeout = 5_000) { frequency(TrendRange.THIRTY_DAYS) }
    }
}
