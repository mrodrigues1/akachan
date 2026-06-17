package com.babytracker.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.trends.DailyFeedVsSleep
import com.babytracker.domain.trends.DailyFeedingCount
import com.babytracker.domain.trends.DailyFeedingInterval
import com.babytracker.domain.trends.DailySleepDuration
import com.babytracker.domain.trends.DayRhythm
import com.babytracker.domain.trends.TrendRange
import com.babytracker.domain.usecase.trends.GetDayRhythmTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedVsSleepTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingFrequencyTrendUseCase
import com.babytracker.domain.usecase.trends.GetFeedingIntervalTrendUseCase
import com.babytracker.domain.usecase.trends.GetSleepDurationTrendUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TrendsUiState(
    val range: TrendRange = TrendRange.SEVEN_DAYS,
    val isLoading: Boolean = true,
    val feedingFrequency: List<DailyFeedingCount> = emptyList(),
    val sleepDuration: List<DailySleepDuration> = emptyList(),
    val feedingInterval: List<DailyFeedingInterval> = emptyList(),
    val feedVsSleep: List<DailyFeedVsSleep> = emptyList(),
    val dayRhythm: List<DayRhythm> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TrendsViewModel @Inject constructor(
    private val getFeedingFrequencyTrend: GetFeedingFrequencyTrendUseCase,
    private val getSleepDurationTrend: GetSleepDurationTrendUseCase,
    private val getFeedingIntervalTrend: GetFeedingIntervalTrendUseCase,
    private val getFeedVsSleepTrend: GetFeedVsSleepTrendUseCase,
    private val getDayRhythmTrend: GetDayRhythmTrendUseCase,
) : ViewModel() {

    private val selectedRange = MutableStateFlow(TrendRange.SEVEN_DAYS)

    val uiState: StateFlow<TrendsUiState> =
        selectedRange.flatMapLatest { range ->
            flow {
                emit(TrendsUiState(range = range, isLoading = true))
                emit(
                    TrendsUiState(
                        range = range,
                        isLoading = false,
                        feedingFrequency = getFeedingFrequencyTrend(range),
                        sleepDuration = getSleepDurationTrend(range),
                        feedingInterval = getFeedingIntervalTrend(range),
                        feedVsSleep = getFeedVsSleepTrend(range),
                        dayRhythm = getDayRhythmTrend(range),
                    ),
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = TrendsUiState(),
        )

    fun onRangeSelected(range: TrendRange) {
        selectedRange.value = range
    }

    companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
