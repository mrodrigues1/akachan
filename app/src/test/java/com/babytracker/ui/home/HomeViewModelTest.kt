package com.babytracker.ui.home

import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.FeedPrediction
import com.babytracker.domain.model.HomeTile
import com.babytracker.domain.model.InventorySummary
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.TodayDiaperSummary
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
import com.babytracker.domain.usecase.diaper.ObserveTodayDiaperSummaryUseCase
import com.babytracker.domain.usecase.feeding.ObserveTodayFeedingSummaryUseCase
import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.domain.model.VaccineSummary
import com.babytracker.domain.usecase.features.GetEnabledFeaturesUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveDoctorVisitSummaryUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineSummaryUseCase
import com.babytracker.domain.usecase.sleep.GetSleepHistoryUseCase
import com.babytracker.domain.usecase.sleep.PredictSleepWindowUseCase
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

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
    private lateinit var observeTodayDiaperSummary: ObserveTodayDiaperSummaryUseCase
    private lateinit var getEnabledFeatures: GetEnabledFeaturesUseCase
    private lateinit var observeVaccineSummary: ObserveVaccineSummaryUseCase
    private lateinit var observeDoctorVisitSummary: ObserveDoctorVisitSummaryUseCase
    private lateinit var logBabyEvent: LogBabyEventUseCase
    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testBaby = Baby(name = "Emma", birthDate = LocalDate.of(2026, 3, 15))
    private val inProgressSession = BreastfeedingSession(
        id = 1L,
        startTime = Instant.now().minusSeconds(300),
        endTime = null,
        startingSide = BreastSide.LEFT
    )
    private val completedSession = BreastfeedingSession(
        id = 2L,
        startTime = Instant.now().minusSeconds(7200),
        endTime = Instant.now().minusSeconds(6900),
        startingSide = BreastSide.RIGHT
    )

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
        observeTodayDiaperSummary = mockk()
        getEnabledFeatures = mockk()
        observeVaccineSummary = mockk()
        observeDoctorVisitSummary = mockk()
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
        every { observeTodayDiaperSummary() } returns flowOf(TodayDiaperSummary())
        every { observeVaccineSummary() } returns flowOf(VaccineSummary())
        every { observeDoctorVisitSummary() } returns flowOf(DoctorVisitSummary())
        every { getEnabledFeatures() } returns flowOf(AppFeature.ALL)
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
        observeTodayDiaperSummary,
        getEnabledFeatures,
        observeVaccineSummary,
        observeDoctorVisitSummary,
        logBabyEvent,
    )

    @Test
    fun activeSession_isNull_whenNoInProgressFeeding() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeSession)
    }

    @Test
    fun activeSession_isSet_whenInProgressFeedingExists() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.activeSession)
        assertEquals(inProgressSession.id, viewModel.uiState.value.activeSession!!.id)
    }

    @Test
    fun nextRecommendedSide_returnsOppositeOfLastCompletedSessionStartingSide_whenNoSwitch() = runTest {
        // completedSession started on RIGHT with no switch → recommend LEFT (the less-used side)
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(BreastSide.LEFT, viewModel.uiState.value.nextRecommendedSide)
    }

    @Test
    fun nextRecommendedSide_isNull_whenNoCompletedSession() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.nextRecommendedSide)
    }

    @Test
    fun nextRecommendedSide_recommendsLessUsedSide_whenSessionHadSwitch() = runTest {
        val now = Instant.now()
        // RIGHT 2m10s (130s), LEFT 19s — LEFT was used less → recommend LEFT (opposite of starting)
        val sessionWithSwitch = BreastfeedingSession(
            id = 3L,
            startTime = now.minusSeconds(300),
            endTime = now.minusSeconds(151),  // session ended
            startingSide = BreastSide.RIGHT,
            switchTime = now.minusSeconds(170) // 19s on RIGHT before switch, then 19s on LEFT
        )
        every { getBreastfeedingHistory() } returns flowOf(listOf(sessionWithSwitch))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        // RIGHT used 130s, LEFT used 19s → LEFT was used less → recommend LEFT
        assertEquals(BreastSide.LEFT, viewModel.uiState.value.nextRecommendedSide)
    }

    @Test
    fun lastSessionStartTime_isNull_whenHistoryIsEmpty() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(emptyList())
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun lastSessionStartTime_equalsActiveSessionStart_whenActiveSessionExists() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(inProgressSession.startTime, viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun lastSessionStartTime_prefersActiveSession_evenWhenNotFirstInList() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession, inProgressSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(inProgressSession.startTime, viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun lastSessionStartTime_equalsMostRecentCompletedStart_whenNoActiveSession() = runTest {
        every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(completedSession.startTime, viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun activeSleepRecord_isNull_whenNoInProgressSleepRecord() = runTest {
        val completedSleep = SleepRecord(
            id = 1L,
            startTime = Instant.now().minusSeconds(3600),
            endTime = Instant.now().minusSeconds(1800),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { getSleepHistory() } returns flowOf(listOf(completedSleep))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.activeSleepRecord)
    }

    @Test
    fun activeSleepRecord_isSet_whenInProgressSleepRecordExists() = runTest {
        val inProgressSleep = SleepRecord(
            id = 2L,
            startTime = Instant.now().minusSeconds(600),
            endTime = null,
            sleepType = SleepType.NAP,
        )
        every { getSleepHistory() } returns flowOf(listOf(inProgressSleep))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.activeSleepRecord)
        assertEquals(inProgressSleep.id, viewModel.uiState.value.activeSleepRecord!!.id)
    }

    @Test
    fun appMode_reflectsRepositoryValue() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(AppMode.PRIMARY, viewModel.uiState.value.appMode)
    }

    @Test
    fun init_firesSyncToFirestore() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify(exactly = 1) { syncToFirestore(any()) }
    }

    @Test
    fun pumpingActive_isNull_whenNoActiveSession() = runTest {
        every { pumpingRepository.getActiveSession() } returns flowOf(null)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.pumpingActive)
    }

    @Test
    fun pumpingActive_isSet_whenActivePumpingSessionExists() = runTest {
        val session = PumpingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            breast = PumpingBreast.LEFT,
        )
        every { pumpingRepository.getActiveSession() } returns flowOf(session)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.pumpingActive)
        assertEquals(session.id, viewModel.uiState.value.pumpingActive!!.id)
    }

    @Test
    fun inventorySummary_defaultsToEmpty_whenRepositoryEmitsEmpty() = runTest {
        every { inventoryRepository.getSummary() } returns flowOf(InventorySummary.Empty)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(InventorySummary.Empty, viewModel.uiState.value.inventorySummary)
    }

    @Test
    fun inventorySummary_flowsThroughUnchanged() = runTest {
        val summary = InventorySummary(totalMl = 240, bagCount = 3, oldestBagDate = null)
        every { inventoryRepository.getSummary() } returns flowOf(summary)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(240, viewModel.uiState.value.inventorySummary.totalMl)
        assertEquals(3, viewModel.uiState.value.inventorySummary.bagCount)
    }

    @Test
    fun nextFeedPrediction_isExposed_whenNoActiveSession() = runTest {
        val prediction = FeedPrediction(
            predictedAt = Instant.parse("2026-05-19T17:40:00Z"),
            averageIntervalMinutes = 180,
            sampleSize = 5,
            isOverdue = false,
            minutesUntil = 45,
        )
        every { getBreastfeedingHistory() } returns flowOf(listOf(completedSession))
        every { predictNextFeed() } returns flowOf(prediction)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(prediction, viewModel.uiState.value.nextFeedPrediction)
    }

    @Test
    fun nextFeedPrediction_isSuppressed_whenActiveSessionExists() = runTest {
        val prediction = FeedPrediction(
            predictedAt = Instant.parse("2026-05-19T17:40:00Z"),
            averageIntervalMinutes = 180,
            sampleSize = 5,
            isOverdue = false,
            minutesUntil = 45,
        )
        every { getBreastfeedingHistory() } returns flowOf(listOf(inProgressSession, completedSession))
        every { predictNextFeed() } returns flowOf(prediction)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.nextFeedPrediction)
    }

    @Test
    fun lastSleepEndTime_isNull_whenNoCompletedSleepExists() = runTest {
        every { getSleepHistory() } returns flowOf(emptyList())
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNull(viewModel.uiState.value.lastSleepEndTime)
    }

    @Test
    fun lastSleepEndTime_isSet_toLatestEndTime() = runTest {
        val earlierEnd = Instant.parse("2026-05-22T08:00:00Z")
        val laterEnd = Instant.parse("2026-05-22T10:00:00Z")
        val older = SleepRecord(
            id = 1L,
            startTime = Instant.parse("2026-05-22T07:00:00Z"),
            endTime = earlierEnd,
            sleepType = SleepType.NAP,
        )
        val newer = SleepRecord(
            id = 2L,
            startTime = Instant.parse("2026-05-22T09:00:00Z"),
            endTime = laterEnd,
            sleepType = SleepType.NAP,
        )
        every { getSleepHistory() } returns flowOf(listOf(newer, older))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(laterEnd, viewModel.uiState.value.lastSleepEndTime)
    }

    @Test
    fun lastSleepEndTime_picksMaxEndTime_notLatestStartTime() = runTest {
        // Record A: later startTime but earlier endTime (appears first in start_time DESC list)
        val endTimeA = Instant.parse("2026-05-22T07:30:00Z")
        val recordA = SleepRecord(
            id = 1L,
            startTime = Instant.parse("2026-05-22T07:00:00Z"),
            endTime = endTimeA,
            sleepType = SleepType.NAP,
        )
        // Record B: earlier startTime but later endTime
        val endTimeB = Instant.parse("2026-05-22T06:00:00Z")
        val recordB = SleepRecord(
            id = 2L,
            startTime = Instant.parse("2026-05-21T22:00:00Z"),
            endTime = endTimeB,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        // recordA appears first (later start_time), but endTimeA > endTimeB
        every { getSleepHistory() } returns flowOf(listOf(recordA, recordB))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(endTimeA, viewModel.uiState.value.lastSleepEndTime)
    }

    @Test
    fun sleepPrediction_flowsThroughToUiState() = runTest {
        val state = SleepPredictionState.CurrentlySleeping
        every { predictSleepWindow() } returns flowOf(state)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(state, viewModel.uiState.value.sleepPrediction)
    }

    @Test
    fun todayFeedingSummary_surfacesFromUseCase() = runTest {
        every { observeTodayFeedingSummary() } returns flowOf(
            TodayFeedingSummary(bottleVolumeMl = 150, bottleCount = 1, breastfeedingCount = 2),
        )
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        val summary = viewModel.uiState.value.todayFeedingSummary
        assertEquals(150, summary.bottleVolumeMl)
        assertEquals(3, summary.totalFeedCount)
    }

    @Test
    fun volumeUnit_reflectsRepositoryValue() = runTest {
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.OZ)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(VolumeUnit.OZ, viewModel.uiState.value.volumeUnit)
    }

    @Test
    fun onCueTapped_delegatesToLogBabyEventUseCase() = runTest {
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onCueTapped(BabyEventType.SLEEPY_CUE)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { logBabyEvent(BabyEventType.SLEEPY_CUE) }
    }
}
