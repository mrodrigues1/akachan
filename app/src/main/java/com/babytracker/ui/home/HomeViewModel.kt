package com.babytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import com.babytracker.domain.usecase.feeding.ObserveTodayFeedingSummaryUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class HomeUiState(
    val baby: Baby? = null,
    val recentFeedings: List<BreastfeedingSession> = emptyList(),
    val recentSleepRecords: List<SleepRecord> = emptyList(),
    val activeSession: BreastfeedingSession? = null,
    val activeSleepRecord: SleepRecord? = null,
    val nextRecommendedSide: BreastSide? = null,
    val lastNightSleepDuration: Duration? = null,
    val lastSessionStartTime: Instant? = null,
    val lastSleepEndTime: Instant? = null,
    val appMode: AppMode = AppMode.NONE,
    val pumpingActive: PumpingSession? = null,
    val inventorySummary: InventorySummary = InventorySummary.Empty,
    val nextFeedPrediction: FeedPrediction? = null,
    val sleepPrediction: SleepPredictionState = SleepPredictionState.Unavailable("loading"),
    val volumeUnit: VolumeUnit = VolumeUnit.ML,
    val todayFeedingSummary: TodayFeedingSummary = TodayFeedingSummary(),
    val tileOrder: List<HomeTile> = HomeTile.DEFAULT_ORDER,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    getBabyProfile: GetBabyProfileUseCase,
    getBreastfeedingHistory: GetBreastfeedingHistoryUseCase,
    getSleepHistory: GetSleepHistoryUseCase,
    private val syncToFirestore: SyncToFirestoreUseCase,
    private val settingsRepository: SettingsRepository,
    pumpingRepository: PumpingRepository,
    inventoryRepository: InventoryRepository,
    predictNextFeed: PredictNextFeedUseCase,
    predictSleepWindow: PredictSleepWindowUseCase,
    observeTodayFeedingSummary: ObserveTodayFeedingSummaryUseCase,
    private val logBabyEvent: LogBabyEventUseCase,
) : ViewModel() {

    private val baseState = combine(
        combine(
            getBabyProfile(),
            getBreastfeedingHistory(),
            getSleepHistory(),
            settingsRepository.getAppMode(),
            predictNextFeed(),
        ) { baby, feedings, sleepRecords, appMode, prediction ->
            val yesterday = LocalDate.now().minusDays(1)
            val zone = ZoneId.systemDefault()

            val lastNightSleep = sleepRecords
                .filter { it.sleepType == SleepType.NIGHT_SLEEP && it.endTime != null }
                .filter { it.startTime.atZone(zone).toLocalDate() == yesterday }
                .mapNotNull { record ->
                    record.endTime?.let { end -> Duration.between(record.startTime, end) }
                }
                .fold(Duration.ZERO) { acc, d -> acc + d }
                .takeIf { !it.isZero }

            val lastCompleted = feedings.firstOrNull { it.endTime != null }
            val nextRecommendedSide = lastCompleted?.let { session ->
                val endTime = session.endTime ?: return@let null
                val oppositeSide = if (session.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
                if (session.switchTime == null) {
                    oppositeSide
                } else {
                    val firstDuration = Duration.between(session.startTime, session.switchTime!!)
                    val secondDuration = Duration.between(session.switchTime!!, endTime)
                    if (secondDuration < firstDuration) oppositeSide else session.startingSide
                }
            }

            HomeUiState(
                baby = baby,
                recentFeedings = feedings.take(3),
                recentSleepRecords = sleepRecords.take(3),
                activeSession = feedings.firstOrNull { it.isInProgress },
                activeSleepRecord = sleepRecords.firstOrNull { it.isInProgress },
                nextRecommendedSide = nextRecommendedSide,
                lastNightSleepDuration = lastNightSleep,
                lastSessionStartTime = (feedings.firstOrNull { it.isInProgress }
                    ?: feedings.firstOrNull())?.startTime,
                lastSleepEndTime = sleepRecords.mapNotNull { it.endTime }.maxOrNull(),
                appMode = appMode,
                nextFeedPrediction = if (feedings.any { it.isInProgress }) null else prediction,
            )
        },
        pumpingRepository.getActiveSession(),
        inventoryRepository.getSummary(),
        predictSleepWindow(),
        settingsRepository.getVolumeUnit(),
    ) { partial, pumpingActive, inventorySummary, sleepPrediction, volumeUnit ->
        partial.copy(
            pumpingActive = pumpingActive,
            inventorySummary = inventorySummary,
            sleepPrediction = sleepPrediction,
            volumeUnit = volumeUnit,
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        baseState,
        observeTodayFeedingSummary(),
        settingsRepository.getHomeTileOrder(),
    ) { base, todayFeedingSummary, tileOrder ->
        base.copy(todayFeedingSummary = todayFeedingSummary, tileOrder = tileOrder)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = HomeUiState(),
    )

    fun onCueTapped(type: BabyEventType) {
        viewModelScope.launch { runCatching { logBabyEvent(type) } }
    }

    fun onTilesReordered(newOrder: List<HomeTile>) {
        viewModelScope.launch { runCatching { settingsRepository.setHomeTileOrder(newOrder) } }
    }

    init {
        viewModelScope.launch { runCatching { syncToFirestore() } }
    }

}
