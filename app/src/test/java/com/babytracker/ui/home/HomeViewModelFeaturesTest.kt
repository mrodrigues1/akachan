package com.babytracker.ui.home

import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.TodayDiaperSummary
import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import com.babytracker.domain.usecase.diaper.ObserveTodayDiaperSummaryUseCase
import com.babytracker.domain.usecase.feeding.ObserveTodayFeedingSummaryUseCase
import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.domain.model.VaccineSummary
import com.babytracker.domain.usecase.doctorvisit.ObserveDoctorVisitSummaryUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineSummaryUseCase
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelFeaturesTest {

    private lateinit var babyRepository: BabyRepository
    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var sleepRepository: SleepRepository
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pumpingRepository: PumpingRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var predictNextFeed: PredictNextFeedUseCase
    private lateinit var predictSleepWindow: PredictSleepWindowUseCase
    private lateinit var observeTodayFeedingSummary: ObserveTodayFeedingSummaryUseCase
    private lateinit var observeTodayDiaperSummary: ObserveTodayDiaperSummaryUseCase
    private lateinit var featureToggleRepository: FeatureToggleRepository
    private lateinit var observeVaccineSummary: ObserveVaccineSummaryUseCase
    private lateinit var observeDoctorVisitSummary: ObserveDoctorVisitSummaryUseCase
    private lateinit var logBabyEvent: LogBabyEventUseCase
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)

    // uiState is a WhileSubscribed StateFlow; subscribe on this testDispatcher-backed scope so
    // advanceUntilIdle() populates uiState.value (previously guaranteed by SharingStarted.Eagerly).
    private lateinit var collectorScope: CoroutineScope

    @BeforeEach
    fun setUp() {
        collectorScope = CoroutineScope(testDispatcher)
        babyRepository = mockk()
        breastfeedingRepository = mockk()
        sleepRepository = mockk()
        syncToFirestore = mockk()
        settingsRepository = mockk()
        pumpingRepository = mockk()
        inventoryRepository = mockk()
        predictNextFeed = mockk()
        predictSleepWindow = mockk()
        observeTodayFeedingSummary = mockk()
        observeTodayDiaperSummary = mockk()
        featureToggleRepository = mockk()
        observeVaccineSummary = mockk()
        observeDoctorVisitSummary = mockk()
        logBabyEvent = mockk()

        every { babyRepository.getBabyProfile() } returns flowOf(Baby(name = "Emma", birthDate = LocalDate.of(2026, 3, 15)))
        every { breastfeedingRepository.getAllSessions() } returns flowOf(emptyList())
        every { sleepRepository.getAllRecords() } returns flowOf(emptyList())
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
        every { settingsRepository.getHomeTileOrder() } returns flowOf(HomeTile.DEFAULT_ORDER)
        every { pumpingRepository.getActiveSession() } returns flowOf(null)
        every { inventoryRepository.getSummary() } returns flowOf(InventorySummary.Empty)
        every { predictNextFeed() } returns flowOf(null)
        every { predictSleepWindow() } returns flowOf(SleepPredictionState.Unavailable("test"))
        every { observeTodayFeedingSummary() } returns flowOf(TodayFeedingSummary())
        every { observeTodayDiaperSummary() } returns flowOf(TodayDiaperSummary())
        every { observeVaccineSummary() } returns flowOf(VaccineSummary())
        every { observeDoctorVisitSummary() } returns flowOf(DoctorVisitSummary())
        coJustRun { syncToFirestore(any()) }
        coJustRun { logBabyEvent(any()) }
    }

    @AfterEach
    fun tearDown() {
        collectorScope.cancel()
    }

    private fun createViewModel(): HomeViewModel {
        val vm = HomeViewModel(
            babyRepository,
            breastfeedingRepository,
            sleepRepository,
            SyncedWrite(syncToFirestore),
            settingsRepository,
            pumpingRepository,
            inventoryRepository,
            predictNextFeed,
            predictSleepWindow,
            observeTodayFeedingSummary,
            observeTodayDiaperSummary,
            featureToggleRepository,
            observeVaccineSummary,
            observeDoctorVisitSummary,
            logBabyEvent,
        )
        collectorScope.launch { vm.uiState.collect {} }
        return vm
    }

    @Test
    fun uiState_reflectsEnabledFeaturesFromUseCase() = runTest {
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(setOf(AppFeature.SLEEP))
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(setOf(AppFeature.SLEEP), viewModel.uiState.value.enabledFeatures)
    }

    @Test
    fun uiState_defaultsToAllFeatures_whenUseCaseEmitsAll() = runTest {
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(AppFeature.ALL)
        val viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppFeature.ALL, viewModel.uiState.value.enabledFeatures)
    }
}
