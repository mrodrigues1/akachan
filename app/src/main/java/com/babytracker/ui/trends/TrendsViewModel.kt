package com.babytracker.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.DayRhythm
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.usecase.trends.GetDayRhythmTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCase
import com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrendsUiState(
    val range: TrendRange = TrendRange.SEVEN_DAYS,
    val isLoading: Boolean = true,
    val feedingFrequency: List<DailyFeedingCount> = emptyList(),
    val sleepDuration: List<DailySleepDuration> = emptyList(),
    val feedingInterval: List<DailyFeedingInterval> = emptyList(),
    val dayRhythm: List<DayRhythm> = emptyList(),
)

/** True when no domain has anything to show: the brand-new, zero-data state. */
fun TrendsUiState.isEmptyOverall(): Boolean =
    dayRhythm.all { it.sleepBlocks.isEmpty() && it.breastFeedMarks.isEmpty() && it.bottleFeedMarks.isEmpty() } &&
        feedingFrequency.all { it.count == 0 } &&
        sleepDuration.all { it.totalHours == 0.0 } &&
        feedingInterval.all { it.averageHours == null }

@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val getFeedingFrequencyTrend: GetFeedingFrequencyTrendUseCase,
    private val getSleepDurationTrend: GetSleepDurationTrendUseCase,
    private val getFeedingIntervalTrend: GetFeedingIntervalTrendUseCase,
    private val getDayRhythmTrend: GetDayRhythmTrendUseCase,
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TrendRange.SEVEN_DAYS)
    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Recompute on every range change; cancel an in-flight load when the range changes again.
            selectedRange.collectLatest { range ->
                // Flip to loading but KEEP the prior range's data on screen, so switching ranges
                // animates in place instead of blanking to a skeleton between every tap. The
                // skeleton then only ever shows on the true first load (when data is still empty).
                _uiState.update { it.copy(range = range, isLoading = true) }
                // Run the four trend computations concurrently instead of serially; each use case
                // does its own CPU work on Dispatchers.Default. collectLatest cancels these children
                // if the range changes again. Wall-clock is now the slowest trend, not their sum.
                coroutineScope {
                    val frequency = async { getFeedingFrequencyTrend(range) }
                    val sleep = async { getSleepDurationTrend(range) }
                    val interval = async { getFeedingIntervalTrend(range) }
                    val rhythm = async { getDayRhythmTrend(range) }
                    _uiState.update {
                        it.copy(
                            range = range,
                            isLoading = false,
                            feedingFrequency = frequency.await(),
                            sleepDuration = sleep.await(),
                            feedingInterval = interval.await(),
                            dayRhythm = rhythm.await(),
                        )
                    }
                }
            }
        }
    }

    fun onRangeSelected(range: TrendRange) {
        selectedRange.value = range
    }
}
