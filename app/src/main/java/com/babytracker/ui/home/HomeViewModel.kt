package com.babytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val baby: Baby? = null,
    val recentFeedings: List<BreastfeedingSession> = emptyList(),
    val recentSleepRecords: List<SleepRecord> = emptyList(),
    val activeSession: BreastfeedingSession? = null,
    val lastFeedSide: BreastSide? = null,
    val sessionsTodayCount: Int = 0,
    val lastNightSleepDuration: Duration? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    getBabyProfile: GetBabyProfileUseCase,
    getBreastfeedingHistory: GetBreastfeedingHistoryUseCase,
    getSleepHistory: GetSleepHistoryUseCase,
    private val stopBreastfeedingSession: StopBreastfeedingSessionUseCase,
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        getBabyProfile(),
        getBreastfeedingHistory(),
        getSleepHistory()
    ) { baby, feedings, sleepRecords ->
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val zone = ZoneId.systemDefault()

        val lastNightSleep = sleepRecords
            .filter { it.sleepType == SleepType.NIGHT_SLEEP && it.endTime != null }
            .filter { it.startTime.atZone(zone).toLocalDate() == yesterday }
            .mapNotNull { record ->
                record.endTime?.let { end -> Duration.between(record.startTime, end) }
            }
            .fold(Duration.ZERO) { acc, d -> acc + d }
            .takeIf { !it.isZero }

        HomeUiState(
            baby = baby,
            recentFeedings = feedings.take(3),
            recentSleepRecords = sleepRecords.take(3),
            activeSession = feedings.firstOrNull { it.isInProgress },
            lastFeedSide = feedings.firstOrNull()?.startingSide,
            sessionsTodayCount = feedings.count {
                it.startTime.atZone(zone).toLocalDate() == today
            },
            lastNightSleepDuration = lastNightSleep
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState()
    )

    fun onStopActiveSession() {
        val session = uiState.value.activeSession ?: return
        viewModelScope.launch { stopBreastfeedingSession(session) }
    }

}
