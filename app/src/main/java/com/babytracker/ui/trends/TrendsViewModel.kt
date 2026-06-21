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
                _uiState.update {
                    it.copy(
                        range = range,
                        isLoading = false,
                        feedingFrequency = getFeedingFrequencyTrend(range),
                        sleepDuration = getSleepDurationTrend(range),
                        feedingInterval = getFeedingIntervalTrend(range),
                        dayRhythm = getDayRhythmTrend(range),
                    )
                }
            }
        }
    }

    fun onRangeSelected(range: TrendRange) {
        selectedRange.value = range
    }
}
