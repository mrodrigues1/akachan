package com.babytracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.babytracker.domain.model.AppFeature
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
import com.babytracker.domain.model.TodayDiaperSummary
import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.domain.model.VaccineSummary
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.model.BabyEventType
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import com.babytracker.domain.usecase.diaper.ObserveTodayDiaperSummaryUseCase
import com.babytracker.domain.usecase.feeding.ObserveTodayFeedingSummaryUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveDoctorVisitSummaryUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineSummaryUseCase
import com.babytracker.domain.usecase.sleep.SharedSleepPredictionStream
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.usecase.SyncedWrite
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val todayDiaperSummary: TodayDiaperSummary = TodayDiaperSummary(),
    val vaccineSummary: VaccineSummary = VaccineSummary(),
    val doctorVisitSummary: DoctorVisitSummary = DoctorVisitSummary(),
    val tileOrder: List<HomeTile> = HomeTile.DEFAULT_ORDER,
    val enabledFeatures: Set<AppFeature> = AppFeature.ALL,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    babyRepository: BabyRepository,
    breastfeedingRepository: BreastfeedingRepository,
    sleepRepository: SleepRepository,
    private val syncedWrite: SyncedWrite,
    private val settingsRepository: SettingsRepository,
    pumpingRepository: PumpingRepository,
    inventoryRepository: InventoryRepository,
    predictNextFeed: PredictNextFeedUseCase,
    sharedSleepPrediction: SharedSleepPredictionStream,
    observeTodayFeedingSummary: ObserveTodayFeedingSummaryUseCase,
    observeTodayDiaperSummary: ObserveTodayDiaperSummaryUseCase,
    featureToggleRepository: FeatureToggleRepository,
    observeVaccineSummary: ObserveVaccineSummaryUseCase,
    observeDoctorVisitSummary: ObserveDoctorVisitSummaryUseCase,
    private val logBabyEvent: LogBabyEventUseCase,
) : ViewModel() {

    private val baseState = combine(
        combine(
            babyRepository.getBabyProfile(),
            breastfeedingRepository.getAllSessions(),
            sleepRepository.getAllRecords(),
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

            val nextRecommendedSide = feedings.firstOrNull { it.endTime != null }?.recommendedNextSide()

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
        sharedSleepPrediction.observe(),
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
        observeTodayDiaperSummary(),
        // combine is capped at 5 typed flows, so fold the features+vaccine+doctor-visit triple
        // into one slot.
        combine(
            featureToggleRepository.getEnabledFeatures(),
            observeVaccineSummary(),
            observeDoctorVisitSummary(),
        ) { features, vaccine, doctor -> Triple(features, vaccine, doctor) },
    ) { base, todayFeedingSummary, tileOrder, todayDiaperSummary, (enabledFeatures, vaccineSummary, doctorVisitSummary) ->
        base.copy(
            todayFeedingSummary = todayFeedingSummary,
            tileOrder = tileOrder,
            todayDiaperSummary = todayDiaperSummary,
            enabledFeatures = enabledFeatures,
            vaccineSummary = vaccineSummary,
            doctorVisitSummary = doctorVisitSummary,
        )
    }.distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            // Was Eagerly, which kept the ~14-flow combine hot for the ViewModel's whole life even
            // when Home is off-screen — including predictSleepWindow() (already moved off the main
            // thread inside PredictSleepWindowUseCase). WhileSubscribed matches every other VM here
            // and stops the graph 5s after the UI stops collecting. distinctUntilChanged drops
            // duplicate UiState emissions so equal recomputations don't trigger recomposition.
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HomeUiState(),
        )

    fun onCueTapped(type: BabyEventType) {
        viewModelScope.launch { runCatching { logBabyEvent(type) } }
    }

    fun onTilesReordered(newOrder: List<HomeTile>) {
        viewModelScope.launch { runCatching { settingsRepository.setHomeTileOrder(newOrder) } }
    }

    fun onResetTileOrder() {
        viewModelScope.launch { runCatching { settingsRepository.clearHomeTileOrder() } }
    }

    init {
        viewModelScope.launch { syncedWrite.sync() }
    }

}
