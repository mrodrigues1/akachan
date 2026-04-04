package com.babytracker.ui.home

import androidx.lifecycle.ViewModel
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

data class HomeUiState(
    val baby: Baby? = null,
    val recentFeedings: List<BreastfeedingSession> = emptyList(),
    val recentSleepRecords: List<SleepRecord> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    getBabyProfile: GetBabyProfileUseCase,
    getBreastfeedingHistory: GetBreastfeedingHistoryUseCase,
    getSleepHistory: GetSleepHistoryUseCase
) : ViewModel() {

    val uiState: Flow<HomeUiState> = combine(
        getBabyProfile(),
        getBreastfeedingHistory(),
        getSleepHistory()
    ) { baby, feedings, sleepRecords ->
        HomeUiState(
            baby = baby,
            recentFeedings = feedings.take(3),
            recentSleepRecords = sleepRecords.take(3)
        )
    }
}
