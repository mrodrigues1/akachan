package com.babytracker.ui.home

import androidx.lifecycle.viewModelScope
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
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.FeatureToggleRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.usecase.baby.LogBabyEventUseCase
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import com.babytracker.domain.usecase.diaper.ObserveTodayDiaperSummaryUseCase
import com.babytracker.domain.usecase.feeding.ObserveTodayFeedingSummaryUseCase
import com.babytracker.domain.model.DoctorVisitSummary
import com.babytracker.domain.model.VaccineSummary
import com.babytracker.domain.usecase.doctorvisit.ObserveDoctorVisitSummaryUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineSummaryUseCase
import com.babytracker.domain.usecase.sleep.SharedSleepPredictionStream
import com.babytracker.sharing.domain.model.AppMode
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coJustRun
import io.mockk.coVerify
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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private lateinit var babyRepository: BabyRepository
    private lateinit var breastfeedingRepository: BreastfeedingRepository
    private lateinit var sleepRepository: SleepRepository
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var pumpingRepository: PumpingRepository
    private lateinit var inventoryRepository: InventoryRepository
    private lateinit var predictNextFeed: PredictNextFeedUseCase
    private lateinit var sharedSleepPrediction: SharedSleepPredictionStream
    private lateinit var observeTodayFeedingSummary: ObserveTodayFeedingSummaryUseCase
    private lateinit var observeTodayDiaperSummary: ObserveTodayDiaperSummaryUseCase
    private lateinit var featureToggleRepository: FeatureToggleRepository
    private lateinit var observeVaccineSummary: ObserveVaccineSummaryUseCase
    private lateinit var observeDoctorVisitSummary: ObserveDoctorVisitSummaryUseCase
    private lateinit var logBabyEvent: LogBabyEventUseCase
    private lateinit var viewModel: HomeViewModel
    private val testDispatcher = StandardTestDispatcher()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(testDispatcher)

    // uiState is a WhileSubscribed StateFlow, so it only runs while collected. Tests subscribe on
    // this scope (backed by testDispatcher) so advanceUntilIdle() drives the pipeline and
    // uiState.value is populated — previously guaranteed by SharingStarted.Eagerly.
    private lateinit var collectorScope: CoroutineScope
    private val createdViewModels = mutableListOf<HomeViewModel>()

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
        collectorScope = CoroutineScope(testDispatcher)
        babyRepository = mockk()
        breastfeedingRepository = mockk()
        sleepRepository = mockk()
        syncToFirestore = mockk()
        settingsRepository = mockk()
        pumpingRepository = mockk()
        inventoryRepository = mockk()
        predictNextFeed = mockk()
        sharedSleepPrediction = mockk()
        observeTodayFeedingSummary = mockk()
        observeTodayDiaperSummary = mockk()
        featureToggleRepository = mockk()
        observeVaccineSummary = mockk()
        observeDoctorVisitSummary = mockk()
        logBabyEvent = mockk()

        every { babyRepository.getBabyProfile() } returns flowOf(testBaby)
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(emptyList())
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(emptyList())
        every { sleepRepository.observeActiveRecord() } returns flowOf(null)
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.NONE)
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.ML)
        every { settingsRepository.getHomeTileOrder() } returns flowOf(HomeTile.DEFAULT_ORDER)
        every { pumpingRepository.getActiveSession() } returns flowOf(null)
        every { inventoryRepository.getSummary() } returns flowOf(InventorySummary.Empty)
        every { predictNextFeed() } returns flowOf(null)
        every { sharedSleepPrediction.observe() } returns flowOf(SleepPredictionState.Unavailable("test"))
        every { observeTodayFeedingSummary() } returns flowOf(TodayFeedingSummary())
        every { observeTodayDiaperSummary() } returns flowOf(TodayDiaperSummary())
        every { observeVaccineSummary() } returns flowOf(VaccineSummary())
        every { observeDoctorVisitSummary() } returns flowOf(DoctorVisitSummary())
        every { featureToggleRepository.getEnabledFeatures() } returns flowOf(AppFeature.ALL)
        coJustRun { syncToFirestore(any()) }
        coJustRun { logBabyEvent(any()) }
    }

    @AfterEach
    fun tearDown() {
        // Cancel each ViewModel's scope while Dispatchers.Main is still the test dispatcher: the
        // flowOn(Default) baseState producer otherwise outlives the test and dispatches into Main
        // after resetMain(), failing a LATER test with UncaughtExceptionsBeforeTest (#788).
        createdViewModels.forEach { it.viewModelScope.cancel() }
        createdViewModels.clear()
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
            sharedSleepPrediction,
            observeTodayFeedingSummary,
            observeTodayDiaperSummary,
            featureToggleRepository,
            observeVaccineSummary,
            observeDoctorVisitSummary,
            logBabyEvent,
        )
        collectorScope.launch { vm.uiState.collect {} }
        createdViewModels += vm
        return vm
    }

    // baseState carries flowOn(Dispatchers.Default), so its combine runs off the test dispatcher and
    // advanceUntilIdle() can return before that background emission crosses back into uiState. Spin the
    // virtual clock until uiState settles to a real (baby-populated) value — baby is always stubbed
    // non-null here — or a real-time safety deadline elapses, so `.value` reads are deterministic.
    private fun settle(vm: HomeViewModel) {
        val deadlineNanos = System.nanoTime() + 5_000_000_000L
        do {
            testDispatcher.scheduler.advanceUntilIdle()
        } while (vm.uiState.value.baby == null && System.nanoTime() < deadlineNanos)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @Test
    fun activeSession_isNull_whenNoInProgressFeeding() = runTest {
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(completedSession))
        viewModel = createViewModel()
        settle(viewModel)
        assertNull(viewModel.uiState.value.activeSession)
    }

    @Test
    fun activeSession_isSet_whenInProgressFeedingExists() = runTest {
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        settle(viewModel)
        assertNotNull(viewModel.uiState.value.activeSession)
        assertEquals(inProgressSession.id, viewModel.uiState.value.activeSession!!.id)
    }

    @Test
    fun nextRecommendedSide_returnsOppositeOfLastCompletedSessionStartingSide_whenNoSwitch() = runTest {
        // completedSession started on RIGHT with no switch → recommend LEFT (the less-used side)
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        settle(viewModel)
        assertEquals(BreastSide.LEFT, viewModel.uiState.value.nextRecommendedSide)
    }

    @Test
    fun nextRecommendedSide_isNull_whenNoCompletedSession() = runTest {
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(inProgressSession))
        viewModel = createViewModel()
        settle(viewModel)
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
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(sessionWithSwitch))
        viewModel = createViewModel()
        settle(viewModel)
        // RIGHT used 130s, LEFT used 19s → LEFT was used less → recommend LEFT
        assertEquals(BreastSide.LEFT, viewModel.uiState.value.nextRecommendedSide)
    }

    @Test
    fun lastSessionStartTime_isNull_whenHistoryIsEmpty() = runTest {
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()
        settle(viewModel)
        assertNull(viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun lastSessionStartTime_equalsActiveSessionStart_whenActiveSessionExists() = runTest {
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(inProgressSession, completedSession))
        viewModel = createViewModel()
        settle(viewModel)
        assertEquals(inProgressSession.startTime, viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun lastSessionStartTime_prefersActiveSession_evenWhenNotFirstInList() = runTest {
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(completedSession, inProgressSession))
        viewModel = createViewModel()
        settle(viewModel)
        assertEquals(inProgressSession.startTime, viewModel.uiState.value.lastSessionStartTime)
    }

    @Test
    fun lastSessionStartTime_equalsMostRecentCompletedStart_whenNoActiveSession() = runTest {
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(completedSession))
        viewModel = createViewModel()
        settle(viewModel)
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(completedSleep))
        viewModel = createViewModel()
        settle(viewModel)
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(inProgressSleep))
        viewModel = createViewModel()
        settle(viewModel)
        assertNotNull(viewModel.uiState.value.activeSleepRecord)
        assertEquals(inProgressSleep.id, viewModel.uiState.value.activeSleepRecord!!.id)
    }

    @Test
    fun activeSleepRecord_surfacesOpenRecordThatStartedBeforeBoundedWindow() = runTest {
        // A stuck/forgotten active sleep older than the since-yesterday window: its start predates the
        // window, but getRecentOrActiveRecordsFlow's `end_time IS NULL` clause still returns it, so the
        // active-sleep card never disappears. Regression guard for the bounded-query change (issue #750).
        val oldActive = SleepRecord(
            id = 99L,
            startTime = Instant.now().minusSeconds(3 * 24 * 3600),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(oldActive))
        viewModel = createViewModel()
        settle(viewModel)

        assertEquals(99L, viewModel.uiState.value.activeSleepRecord?.id)
        assertEquals(oldActive, viewModel.uiState.value.recentSleepRecords.firstOrNull())
    }

    @Test
    fun appMode_reflectsRepositoryValue() = runTest {
        every { settingsRepository.getAppMode() } returns flowOf(AppMode.PRIMARY)
        viewModel = createViewModel()
        settle(viewModel)
        assertEquals(AppMode.PRIMARY, viewModel.uiState.value.appMode)
    }

    @Test
    fun init_firesSyncToFirestore() = runTest {
        viewModel = createViewModel()
        settle(viewModel)
        coVerify(exactly = 1) { syncToFirestore(any()) }
    }

    @Test
    fun pumpingActive_isNull_whenNoActiveSession() = runTest {
        every { pumpingRepository.getActiveSession() } returns flowOf(null)
        viewModel = createViewModel()
        settle(viewModel)
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
        settle(viewModel)
        assertNotNull(viewModel.uiState.value.pumpingActive)
        assertEquals(session.id, viewModel.uiState.value.pumpingActive!!.id)
    }

    @Test
    fun inventorySummary_defaultsToEmpty_whenRepositoryEmitsEmpty() = runTest {
        every { inventoryRepository.getSummary() } returns flowOf(InventorySummary.Empty)
        viewModel = createViewModel()
        settle(viewModel)
        assertEquals(InventorySummary.Empty, viewModel.uiState.value.inventorySummary)
    }

    @Test
    fun inventorySummary_flowsThroughUnchanged() = runTest {
        val summary = InventorySummary(totalMl = 240, bagCount = 3, oldestBagDate = null)
        every { inventoryRepository.getSummary() } returns flowOf(summary)
        viewModel = createViewModel()
        settle(viewModel)
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
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(completedSession))
        every { predictNextFeed() } returns flowOf(prediction)
        viewModel = createViewModel()
        settle(viewModel)
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
        every { breastfeedingRepository.getRecentSessionsFlow(any()) } returns flowOf(listOf(inProgressSession, completedSession))
        every { predictNextFeed() } returns flowOf(prediction)
        viewModel = createViewModel()
        settle(viewModel)
        assertNull(viewModel.uiState.value.nextFeedPrediction)
    }

    @Test
    fun lastSleepEndTime_isNull_whenNoCompletedSleepExists() = runTest {
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(emptyList())
        viewModel = createViewModel()
        settle(viewModel)
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(newer, older))
        viewModel = createViewModel()
        settle(viewModel)
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
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(recordA, recordB))
        viewModel = createViewModel()
        settle(viewModel)
        assertEquals(endTimeA, viewModel.uiState.value.lastSleepEndTime)
    }

    @Test
    fun lastNightSleepDuration_sumsYesterdayNightSleepsFromBoundedWindow() = runTest {
        // The since-yesterday bounded window must still cover last night's night sleep. Build the
        // records relative to the real "yesterday" so the ViewModel's own LocalDate.now() filter matches.
        val zone = java.time.ZoneId.systemDefault()
        val yesterday = LocalDate.now().minusDays(1)
        val nightSleep = SleepRecord(
            id = 1L,
            startTime = yesterday.atTime(22, 0).atZone(zone).toInstant(),
            endTime = yesterday.atTime(23, 30).atZone(zone).toInstant(),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        // A yesterday nap must not be mistaken for night sleep even though it is inside the window.
        val yesterdayNap = SleepRecord(
            id = 2L,
            startTime = yesterday.atTime(14, 0).atZone(zone).toInstant(),
            endTime = yesterday.atTime(15, 0).atZone(zone).toInstant(),
            sleepType = SleepType.NAP,
        )
        every { sleepRepository.getRecentOrActiveRecordsFlow(any()) } returns flowOf(listOf(nightSleep, yesterdayNap))
        viewModel = createViewModel()
        settle(viewModel)

        assertEquals(java.time.Duration.ofMinutes(90), viewModel.uiState.value.lastNightSleepDuration)
    }

    @Test
    fun sleepPrediction_flowsThroughToUiState() = runTest {
        val state = SleepPredictionState.CurrentlySleeping
        every { sharedSleepPrediction.observe() } returns flowOf(state)
        viewModel = createViewModel()
        settle(viewModel)
        assertEquals(state, viewModel.uiState.value.sleepPrediction)
    }

    @Test
    fun todayFeedingSummary_surfacesFromUseCase() = runTest {
        every { observeTodayFeedingSummary() } returns flowOf(
            TodayFeedingSummary(bottleVolumeMl = 150, bottleCount = 1, breastfeedingCount = 2),
        )
        viewModel = createViewModel()
        settle(viewModel)
        val summary = viewModel.uiState.value.todayFeedingSummary
        assertEquals(150, summary.bottleVolumeMl)
        assertEquals(3, summary.totalFeedCount)
    }

    @Test
    fun volumeUnit_reflectsRepositoryValue() = runTest {
        every { settingsRepository.getVolumeUnit() } returns flowOf(VolumeUnit.OZ)
        viewModel = createViewModel()
        settle(viewModel)
        assertEquals(VolumeUnit.OZ, viewModel.uiState.value.volumeUnit)
    }

    @Test
    fun onCueTapped_delegatesToLogBabyEventUseCase() = runTest {
        viewModel = createViewModel()
        settle(viewModel)

        viewModel.onCueTapped(BabyEventType.SLEEPY_CUE)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { logBabyEvent(BabyEventType.SLEEPY_CUE) }
    }
}
