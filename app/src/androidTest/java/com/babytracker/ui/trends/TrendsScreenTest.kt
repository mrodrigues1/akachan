package com.babytracker.ui.trends

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.babytracker.domain.model.ThemeConfig
import com.babytracker.domain.trends.DailyFeedVsSleep
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.DayRhythm
import com.babytracker.domain.trends.RhythmBlock
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.usecase.trends.GetDayRhythmTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedVsSleepTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCase
import com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCase
import com.babytracker.ui.theme.BabyTrackerTheme
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class TrendsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val frequency = mockk<GetFeedingFrequencyTrendUseCase>()
    private val sleep = mockk<GetSleepDurationTrendUseCase>()
    private val interval = mockk<GetFeedingIntervalTrendUseCase>()
    private val feedVsSleep = mockk<GetFeedVsSleepTrendUseCase>()
    private val rhythm = mockk<GetDayRhythmTrendUseCase>()

    private fun newViewModel() = TrendsViewModel(frequency, sleep, interval, feedVsSleep, rhythm)

    private fun emptyViewModel(): TrendsViewModel {
        coEvery { frequency(any()) } returns emptyList()
        coEvery { sleep(any()) } returns emptyList()
        coEvery { interval(any()) } returns emptyList()
        coEvery { feedVsSleep(any()) } returns emptyList()
        coEvery { rhythm(any()) } returns emptyList()
        return newViewModel()
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
        setScreen(emptyViewModel())

        composeRule.onNodeWithTag("trends_range_7").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_range_14").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_range_30").assertIsDisplayed()

        // With no data every chart shows its empty placeholder rather than a chart.
        composeRule.onNodeWithTag("trends_feeding_chart_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_sleep_chart_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_interval_chart_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_feedvssleep_chart_empty").assertIsDisplayed()
        composeRule.onNodeWithTag("trends_rhythm_chart_empty").assertIsDisplayed()
    }

    @Test
    fun chartsRenderWithDataWithoutCrashing() {
        // Regression for AKA-152: a date axis formatter that returned a blank string for the
        // out-of-range x-values Vico probes during label-width measurement crashed the chart while
        // drawing. Rendering populated charts across ranges must complete without throwing.
        val today = LocalDate.of(2026, 6, 16)
        fun day(offset: Int) = today.minusDays((29 - offset).toLong())
        coEvery { frequency(any()) } returns List(30) { DailyFeedingCount(day(it), (it % 9) + 1) }
        coEvery { sleep(any()) } returns List(30) { DailySleepDuration(day(it), nightHours = 8.0, napHours = 4.0) }
        coEvery { interval(any()) } returns List(30) { DailyFeedingInterval(day(it), averageHours = 3.0) }
        coEvery { feedVsSleep(any()) } returns List(30) { DailyFeedVsSleep(day(it), (it % 9) + 1, 12.0) }
        coEvery { rhythm(any()) } returns List(30) {
            DayRhythm(
                date = day(it),
                sleepBlocks = listOf(RhythmBlock(0.0f, 0.25f, isNight = true)),
                breastFeedMarks = listOf(0.3f),
                bottleFeedMarks = listOf(0.6f),
            )
        }

        setScreen(newViewModel())

        // The charts live in a vertical scroll column; the feed-vs-sleep and rhythm charts sit
        // below the fold, so scroll each into view (which also forces the draw pass this test guards)
        // before asserting it is displayed.
        composeRule.onNodeWithTag("trends_feedvssleep_chart").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trends_rhythm_chart").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trends_range_30").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("trends_feedvssleep_chart").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("trends_rhythm_chart").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun selectingRangeRecomputes() {
        setScreen(emptyViewModel())

        composeRule.onNodeWithTag("trends_range_30").performClick()
        composeRule.waitForIdle()

        coVerify(timeout = 5_000) { frequency(TrendRange.THIRTY_DAYS) }
    }
}
