package com.babytracker.ui.home

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.TodayFeedingSummary
import com.babytracker.domain.model.VolumeUnit
import com.babytracker.domain.repository.InventoryRepository
import com.babytracker.domain.repository.PumpingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.baby.GetBabyProfileUseCase
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import com.babytracker.domain.usecase.feeding.ObserveTodayFeedingSummaryUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTileOrderTest {

    private lateinit var getBabyProfile: GetBabyProfileUseCase
    private lateinit var getBreastfeedingHistory: GetBreastfeedingHistoryUseCase
    private lateinit var getSleepHistory: GetSleepHistoryUseCase
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pumpingRepository: PumpingRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var predictNextFeed: PredictNextFeedUseCase
    private lateinit var predictSleepWindow: PredictSleepWindowUseCase
    private lateinit var observeTodayFeedingSummary: ObserveTodayFeedingSummaryUseCase
    private lateinit var logBabyEvent: LogBabyEventUseCase
    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testBaby = Baby(name = "Emma", birthDate = LocalDate.of(2026, 3, 15))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getBabyProfile = mockk()
        getBreastfeedingHistory = mockk()
        getSleepHistory = mockk()
        syncToFirestore = mockk()
        settingsRepository = mockk()
        pumpingRepository = mockk()
        inventoryRepository = mockk()
        predictNextFeed = mockk()
        predictSleepWindow = mockk()
        observeTodayFeedingSummary = mockk()
        logBabyEvent = mockk()

        every { getBabyProfile() } returns flowOf(testBaby)
        every { getBreastfeedingHistory() } returns flowOf(emptyList())
        every { getSleepHistory() } returns flowOf(emptyList())
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
        every { settingsRepository.getHomeTileOrder() } returns flowOf(HomeTile.DEFAULT_ORDER)
        every { pumpingRepository.getActiveSession() } returns flowOf(null)
        every { inventoryRepository.getSummary() } returns flowOf(InventorySummary.Empty)
        every { predictNextFeed() } returns flowOf(null)
        every { predictSleepWindow() } returns flowOf(SleepPredictionState.Unavailable("test"))
        every { observeTodayFeedingSummary() } returns flowOf(TodayFeedingSummary())
        coJustRun { syncToFirestore(any()) }
        coJustRun { logBabyEvent(any()) }
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        getBabyProfile,
        getBreastfeedingHistory,
        getSleepHistory,
        syncToFirestore,
        settingsRepository,
        pumpingRepository,
        inventoryRepository,
        predictNextFeed,
        predictSleepWindow,
        observeTodayFeedingSummary,
        logBabyEvent,
    )

    @Test
    fun uiState_exposesPersistedTileOrder() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(HomeTile.DEFAULT_ORDER, viewModel.uiState.value.tileOrder)
    }

    @Test
    fun uiState_surfacesCustomStoredOrder() = runTest {
        val custom = listOf(
            HomeTile.SLEEP,
            HomeTile.BREASTFEEDING,
            HomeTile.FEEDING_HISTORY,
            HomeTile.BOTTLE_FEED,
            HomeTile.INVENTORY,
            HomeTile.PUMPING,
            HomeTile.SLEEP_PREDICTION,
            HomeTile.TIP,
            HomeTile.PARTNER,
        )
        every { settingsRepository.getHomeTileOrder() } returns flowOf(custom)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(custom, viewModel.uiState.value.tileOrder)
    }

    @Test
    fun onTilesReordered_persistsNewOrder() = runTest {
        val slot = slot<List<HomeTile>>()
        coEvery { settingsRepository.setHomeTileOrder(capture(slot)) } returns Unit
        viewModel = createViewModel()
        val moved = listOf(HomeTile.SLEEP) + HomeTile.DEFAULT_ORDER.filter { it != HomeTile.SLEEP }
        viewModel.onTilesReordered(moved)
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { settingsRepository.setHomeTileOrder(moved) }
        assertEquals(moved, slot.captured)
    }

    @Test
    fun onResetTileOrder_clearsPersistedOrder() = runTest {
        coEvery { settingsRepository.clearHomeTileOrder() } returns Unit
        viewModel = createViewModel()
        viewModel.onResetTileOrder()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { settingsRepository.clearHomeTileOrder() }
    }
}
