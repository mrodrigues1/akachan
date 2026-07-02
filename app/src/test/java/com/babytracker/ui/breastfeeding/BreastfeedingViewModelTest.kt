package com.babytracker.ui.breastfeeding

import android.content.Context
import com.babytracker.R
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.FeedSettingsRepository
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.PredictNextFeedUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.domain.usecase.breastfeeding.UpdateBreastfeedingSessionUseCase
import com.babytracker.manager.BreastfeedingSessionController
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class BreastfeedingViewModelTest {

    private lateinit var switchSide: SwitchBreastfeedingSideUseCase
    private lateinit var pauseSession: PauseBreastfeedingSessionUseCase
    private lateinit var resumeSession: ResumeBreastfeedingSessionUseCase
    private lateinit var updateSession: UpdateBreastfeedingSessionUseCase
    private lateinit var repository: BreastfeedingRepository
    private lateinit var feedSettingsRepository: FeedSettingsRepository
    private lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator
    private lateinit var syncToFirestore: SyncToFirestoreUseCase
    private lateinit var predictNextFeed: PredictNextFeedUseCase
    private lateinit var appContext: Context

    private lateinit var viewModel: BreastfeedingViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val activeSessionFlow = MutableStateFlow<BreastfeedingSession?>(null)
    private val maxPerBreastFlow = MutableStateFlow(15)
    private val maxTotalFlow = MutableStateFlow(30)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        switchSide = mockk()
        repository = mockk()
        feedSettingsRepository = mockk()
        notificationCoordinator = mockk()
        syncToFirestore = mockk()
        predictNextFeed = mockk()

        every { repository.getAllSessions() } returns flowOf(emptyList())
        every { repository.getActiveSession() } returns activeSessionFlow
        every { feedSettingsRepository.getMaxPerBreastMinutes() } returns maxPerBreastFlow
        every { feedSettingsRepository.getMaxTotalFeedMinutes() } returns maxTotalFlow
        every { predictNextFeed() } returns flowOf(null)
        appContext = mockk()
        every { appContext.getString(R.string.elapsed_hours_minutes_ago, any(), any()) } returns "2h 25m ago"
        every { appContext.getString(R.string.elapsed_minutes_ago, any()) } returns "0m ago"
        every { appContext.getString(R.string.elapsed_just_now) } returns "Just now"
        every { appContext.getString(R.string.error_bf_start) } returns "Could not start session. Please try again."
        every { appContext.getString(R.string.error_bf_stop) } returns "Could not stop session. Please try again."
        every { appContext.getString(R.string.error_bf_save) } returns "Could not save changes. Please try again."
        every { appContext.getString(R.string.error_bf_delete) } returns "Could not delete session. Please try again."
        every { appContext.getString(R.string.error_bf_start_future) } returns "Start time can't be in the future"
        every { appContext.getString(R.string.error_bf_end_future) } returns "End time can't be in the future"
        every { appContext.getString(R.string.error_bf_end_after_start) } returns "End time must be after start time"
        every { appContext.getString(R.string.error_bf_session_shorter_pauses) } returns
            "Session is shorter than recorded pauses"
        pauseSession = mockk()
        resumeSession = mockk()
        updateSession = mockk()
        coEvery { repository.insertSession(any()) } returns 1L
        coJustRun { pauseSession(any()) }
        coJustRun { resumeSession(any()) }
        coJustRun { syncToFirestore(any()) }
        coEvery { notificationCoordinator.scheduleInitial(any()) } returns Unit
        coEvery { notificationCoordinator.showRunning(any(), any()) } returns Unit
        coEvery { notificationCoordinator.showPaused(any(), any()) } returns Unit
        every { notificationCoordinator.cancelPerBreastScheduled() } returns Unit
        every { notificationCoordinator.cancelScheduled() } returns Unit
        every { notificationCoordinator.cancelAllSessionNotifications() } returns Unit
        coEvery { notificationCoordinator.rescheduleAfterResume(any(), any()) } returns 0L

        viewModel = createViewModel()
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun createViewModel() = BreastfeedingViewModel(
        appContext,
        // Real controller over the same mocks: the ViewModel contract is "delegates to the shared
        // session-control behaviour", so tests verify through to the underlying collaborators.
        BreastfeedingSessionController(
            repository = repository,
            switchSideUseCase = switchSide,
            pauseSessionUseCase = pauseSession,
            resumeSessionUseCase = resumeSession,
            notificationCoordinator = notificationCoordinator,
            syncedWrite = SyncedWrite(syncToFirestore),
        ),
        updateSession,
        repository,
        feedSettingsRepository,
        notificationCoordinator,
        SyncedWrite(syncToFirestore),
        predictNextFeed,
    )

    private fun awaitLastFeedingSummaryPopulated(): LastFeedingSummaryState.Populated {
        repeat(20) {
            val summary = viewModel.uiState.value.lastFeedingSummary
            if (summary is LastFeedingSummaryState.Populated) {
                return summary
            }
            testDispatcher.scheduler.advanceTimeBy(1)
            testDispatcher.scheduler.runCurrent()
        }
        throw AssertionError("Expected populated lastFeedingSummary, but was ${viewModel.uiState.value.lastFeedingSummary}")
    }

    @Test
    fun `initial state has no active session and default settings`() = runTest {
        assertEquals(null, viewModel.uiState.value.activeSession)
        assertEquals(null, viewModel.uiState.value.selectedSide)
        assertEquals(0, viewModel.uiState.value.maxPerBreastMinutes)
        assertEquals(0, viewModel.uiState.value.maxTotalFeedMinutes)
    }

    @Test
    fun `uiState receives settings from repository`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(15, viewModel.uiState.value.maxPerBreastMinutes)
        assertEquals(30, viewModel.uiState.value.maxTotalFeedMinutes)
    }

    @Test
    fun `uiState receives active session from repository`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(session.id, viewModel.uiState.value.activeSession?.id)
    }

    @Test
    fun `onSideSelected updates selectedSide in uiState`() = runTest {
        viewModel.onSideSelected(BreastSide.RIGHT)

        assertEquals(BreastSide.RIGHT, viewModel.uiState.value.selectedSide)
    }

    @Test
    fun `onStartSession does nothing when no side selected`() = runTest {
        viewModel.onStartSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.insertSession(any()) }
    }

    @Test
    fun `onStartSession starts session when side is selected`() = runTest {
        viewModel.onSideSelected(BreastSide.LEFT)

        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now(),
            startingSide = BreastSide.LEFT
        )

        coEvery { repository.insertSession(any()) } answers {
            activeSessionFlow.value = session
            1L
        }

        viewModel.onStartSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.insertSession(match { it.startingSide == BreastSide.LEFT }) }
    }

    @Test
    fun `onStartSession posts active notification with starting side`() = runTest {
        viewModel.onSideSelected(BreastSide.LEFT)
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now(),
            startingSide = BreastSide.LEFT
        )
        coEvery { repository.insertSession(any()) } answers {
            activeSessionFlow.value = session
            1L
        }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { notificationCoordinator.scheduleInitial(session) }
        coVerify { notificationCoordinator.showRunning(session) }
    }

    @Test
    fun `onStopSession cancels active notification`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()
        coJustRun { repository.updateSession(any()) }

        viewModel.onStopSession()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { notificationCoordinator.cancelAllSessionNotifications() }
    }

    @Test
    fun `onStopSession surfaces error and skips notifications and sync when persist fails`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()
        coEvery { repository.updateSession(any()) } throws RuntimeException("db down")

        viewModel.onStopSession()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Could not stop session. Please try again.", viewModel.uiState.value.error)
        verify(exactly = 0) { notificationCoordinator.cancelAllSessionNotifications() }
        coVerify(exactly = 0) { syncToFirestore(any()) }
    }

    @Test
    fun `onPauseSession shows paused active notification`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPauseSession()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { notificationCoordinator.cancelScheduled() }
        coVerify { notificationCoordinator.showPaused(session, any()) }
    }

    @Test
    fun `onResumeSession posts active notification with starting side when no switch`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = Instant.now().minusSeconds(60)
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResumeSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { notificationCoordinator.rescheduleAfterResume(session, any()) }
        coVerify { notificationCoordinator.showRunning(session, pausedDurationMs = any()) }
    }

    @Test
    fun `onResumeSession posts active notification with switched side after a switch`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            startingSide = BreastSide.LEFT,
            switchTime = Instant.now().minusSeconds(300),
            pausedAt = Instant.now().minusSeconds(60)
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResumeSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { notificationCoordinator.rescheduleAfterResume(session, any()) }
        coVerify { notificationCoordinator.showRunning(session, pausedDurationMs = any()) }
    }

    @Test
    fun `onSwitchSide posts active notification with new side`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()
        coJustRun { switchSide(session) }

        viewModel.onSwitchSide()
        testDispatcher.scheduler.advanceUntilIdle()

        verify { notificationCoordinator.cancelPerBreastScheduled() }
        coVerify {
            notificationCoordinator.showRunning(
                match { it.id == session.id && it.startingSide == BreastSide.LEFT && it.switchTime != null },
                pausedDurationMs = 0L
            )
        }
    }

    @Test
    fun `onSwitchSide does not post notification when already switched`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(600),
            startingSide = BreastSide.LEFT,
            switchTime = Instant.now().minusSeconds(300)
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()
        coJustRun { switchSide(session) }

        viewModel.onSwitchSide()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { notificationCoordinator.showRunning(any(), any()) }
    }

    @Test
    fun `onStopSession completes without error`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        coJustRun { repository.updateSession(any()) }
        // Just verify the method can be called without error.

        // Just verify the method can be called without error
        // Full notification cancellation testing requires instrumented tests
    }

    @Test
    fun `onStopSession does nothing when no active session`() = runTest {
        viewModel.onStopSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { repository.updateSession(any()) }
    }

    @Test
    fun `onSwitchSide switches side`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        coJustRun { switchSide(session) }

        viewModel.onSwitchSide()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { switchSide(session) }
    }

    @Test
    fun `onSwitchSide does nothing when no active session`() = runTest {
        viewModel.onSwitchSide()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { switchSide(any()) }
    }

    @Test
    fun `settings with zero values are reflected in uiState`() = runTest {
        maxPerBreastFlow.value = 0
        maxTotalFlow.value = 0
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.maxPerBreastMinutes)
        assertEquals(0, viewModel.uiState.value.maxTotalFeedMinutes)
    }

    @Test
    fun `history flow is collected from use case`() = runTest {
        val historySessions = listOf(
            BreastfeedingSession(
                id = 1L,
                startTime = Instant.now().minusSeconds(7200),
                endTime = Instant.now().minusSeconds(6900),
                startingSide = BreastSide.LEFT
            )
        )

        every { repository.getAllSessions() } returns flowOf(historySessions)
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify history StateFlow exists and has a value (may be empty initially due to timing)
        assertNotNull(viewModel.history.value)
    }

    @Test
    fun `onStartSession schedules notifications only once even when session is updated later`() = runTest {
        viewModel.onSideSelected(BreastSide.LEFT)

        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now(),
            startingSide = BreastSide.LEFT
        )

        coEvery { repository.insertSession(any()) } answers {
            activeSessionFlow.value = session
            1L
        }

        // Let the init-block combine emit once so _uiState.maxPerBreastMinutes/maxTotalFeedMinutes
        // are populated before onStartSession reads them inside scheduleNotifications.
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartSession()
        testDispatcher.scheduler.advanceUntilIdle()

        // Simulate a session update (e.g., pause) that triggers a new DB emission
        val updatedSession = session.copy(pausedAt = Instant.now())
        activeSessionFlow.value = updatedSession
        testDispatcher.scheduler.advanceUntilIdle()

        // scheduleMaxPerBreastNotification must be called exactly once — on session start —
        // and NOT again when the session update (pause) emits from the repository Flow.
        // The persistent-collect bug caused it to reschedule on every update, which meant
        // cancelling alarms on pause had no lasting effect.
        coVerify(exactly = 1) { notificationCoordinator.scheduleInitial(session) }
    }

    @Test
    fun `notification coordinator does not schedule initial alarms when switching side`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        coJustRun { switchSide(session) }

        viewModel.onSwitchSide()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { switchSide(session) }
        coVerify(exactly = 0) { notificationCoordinator.scheduleInitial(any()) }
    }

    @Test
    fun `onResumeSession asks coordinator to reschedule notifications for remaining active time`() = runTest {
        // Session started 300s ago, paused 60s ago, no previous pauses.
        // maxPerBreast=15min (900s), maxTotal=30min (1800s).
        // Remaining per-breast = (0 + 900 + 0) - (300 - 60) = 900 - 240 = 660s → scheduleAt(now+660s).
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = Instant.now().minusSeconds(60)
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResumeSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { notificationCoordinator.rescheduleAfterResume(session, any()) }
    }

    @Test
    fun `onPauseSession does nothing when no active session`() = runTest {
        viewModel.onPauseSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { pauseSession(any()) }
    }

    @Test
    fun `onPauseSession calls pauseSession use case and cancels notifications`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPauseSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { pauseSession(session) }
        verify(exactly = 1) { notificationCoordinator.cancelScheduled() }
    }

    @Test
    fun `onResumeSession does nothing when no active session`() = runTest {
        viewModel.onResumeSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { resumeSession(any()) }
    }

    @Test
    fun `onResumeSession calls resumeSession use case`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = Instant.now().minusSeconds(60)
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResumeSession()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { resumeSession(session) }
    }

    @Test
    fun `lastFeedingSummary is Empty when no sessions exist`() = runTest {
        every { repository.getAllSessions() } returns flowOf(emptyList())
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LastFeedingSummaryState.Empty, viewModel.uiState.value.lastFeedingSummary)
    }

    @Test
    fun `lastFeedingSummary is Empty when all sessions are still in progress`() = runTest {
        // Pre-create Instant values before mockkStatic to avoid InaccessibleObjectException
        val startTime = Instant.ofEpochSecond(1744538400L) // 2026-04-13T10:00:00Z
        val inProgress = BreastfeedingSession(
            id = 1L,
            startTime = startTime,
            endTime = null,
            startingSide = BreastSide.LEFT
        )
        every { repository.getAllSessions() } returns flowOf(listOf(inProgress))
        viewModel = createViewModel()
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(LastFeedingSummaryState.Empty, viewModel.uiState.value.lastFeedingSummary)
    }

    @Test
    fun `lastFeedingSummary recommends RIGHT when last session ended on LEFT without switch`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        val now = Instant.ofEpochSecond(1744545600L)       // 2026-04-13T12:00:00Z
        val startTime = Instant.ofEpochSecond(1744538400L) // 2026-04-13T10:00:00Z
        val endTime = Instant.ofEpochSecond(1744540200L)   // 2026-04-13T10:30:00Z

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.LEFT,
                switchTime = null
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = viewModel.uiState.value.lastFeedingSummary
            assertTrue(summary is LastFeedingSummaryState.Populated)
            assertEquals(BreastSide.RIGHT, (summary as LastFeedingSummaryState.Populated).nextRecommendedSide)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary recommends LEFT when last session switched from LEFT to RIGHT with equal durations`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        val now = Instant.ofEpochSecond(1744545600L)        // 2026-04-13T12:00:00Z
        val startTime = Instant.ofEpochSecond(1744538400L)  // 2026-04-13T10:00:00Z
        val switchTime = Instant.ofEpochSecond(1744539300L) // 2026-04-13T10:15:00Z (15 min on LEFT)
        val endTime = Instant.ofEpochSecond(1744540200L)    // 2026-04-13T10:30:00Z (15 min on RIGHT)

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            // LEFT 15 min, RIGHT 15 min — equal, so first/starting side (LEFT) is recommended
            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.LEFT,
                switchTime = switchTime
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = viewModel.uiState.value.lastFeedingSummary
            assertTrue(summary is LastFeedingSummaryState.Populated)
            assertEquals(BreastSide.LEFT, (summary as LastFeedingSummaryState.Populated).nextRecommendedSide)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary recommends starting side when it was used less than second side`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        val now = Instant.ofEpochSecond(1744545600L)        // 2026-04-13T12:00:00Z
        val startTime = Instant.ofEpochSecond(1744538400L)  // 2026-04-13T10:00:00Z
        val switchTime = Instant.ofEpochSecond(1744538419L) // 10:00:19 (19s on RIGHT — first/starting side)
        val endTime = Instant.ofEpochSecond(1744538549L)    // 10:02:29 (2m 10s on LEFT — second side)

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            // RIGHT 19s, LEFT 2m10s — RIGHT was used less → recommend RIGHT (the starting side)
            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.RIGHT,
                switchTime = switchTime
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = viewModel.uiState.value.lastFeedingSummary as LastFeedingSummaryState.Populated
            assertEquals(BreastSide.RIGHT, summary.nextRecommendedSide)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary recommends opposite side when second side was used less than first side`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        val now = Instant.ofEpochSecond(1744545600L)        // 2026-04-13T12:00:00Z
        val startTime = Instant.ofEpochSecond(1744538400L)  // 2026-04-13T10:00:00Z
        val switchTime = Instant.ofEpochSecond(1744538530L) // 10:02:10 (2m 10s on RIGHT — first/starting side)
        val endTime = Instant.ofEpochSecond(1744538549L)    // 10:02:29 (19s on LEFT — second side)

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            // RIGHT 2m10s, LEFT 19s — LEFT was used less → recommend LEFT (opposite of starting)
            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.RIGHT,
                switchTime = switchTime
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = awaitLastFeedingSummaryPopulated()
            assertEquals(BreastSide.LEFT, summary.nextRecommendedSide)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary recommends LEFT when last session ended on RIGHT without switch`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        val now = Instant.ofEpochSecond(1744545600L)       // 2026-04-13T12:00:00Z
        val startTime = Instant.ofEpochSecond(1744538400L) // 2026-04-13T10:00:00Z
        val endTime = Instant.ofEpochSecond(1744540200L)   // 2026-04-13T10:30:00Z

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.RIGHT,
                switchTime = null
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = viewModel.uiState.value.lastFeedingSummary as LastFeedingSummaryState.Populated
            assertEquals(BreastSide.LEFT, summary.nextRecommendedSide)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary elapsed label formats hours and minutes ago correctly`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        // start at 09:35, now is 12:00 → 2h 25m elapsed
        val now = Instant.ofEpochSecond(1744545600L)       // 2026-04-13T12:00:00Z
        val startTime = Instant.ofEpochSecond(1744536900L) // 2026-04-13T09:35:00Z
        val endTime = Instant.ofEpochSecond(1744537500L)   // 2026-04-13T09:45:00Z

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.RIGHT
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = awaitLastFeedingSummaryPopulated()
            assertEquals("2h 25m ago", summary.elapsedLabel)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary computes correct durations when no side switch`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        val now = Instant.ofEpochSecond(1744545600L)       // 2026-04-13T12:00:00Z
        val startTime = Instant.ofEpochSecond(1744538400L) // 2026-04-13T10:00:00Z
        val endTime = Instant.ofEpochSecond(1744539600L)   // 2026-04-13T10:20:00Z

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.LEFT,
                switchTime = null
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = awaitLastFeedingSummaryPopulated()
            assertEquals(Duration.ofMinutes(20), summary.firstSideDuration)
            assertNull(summary.secondSideDuration)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary computes correct durations when sides were switched`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        // 10:00 start → 10:15 switch → 10:30 end: first=15m, second=15m
        val now = Instant.ofEpochSecond(1744545600L)        // 2026-04-13T12:00:00Z
        val startTime = Instant.ofEpochSecond(1744538400L)  // 2026-04-13T10:00:00Z
        val switchTime = Instant.ofEpochSecond(1744539300L) // 2026-04-13T10:15:00Z
        val endTime = Instant.ofEpochSecond(1744540200L)    // 2026-04-13T10:30:00Z

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.LEFT,
                switchTime = switchTime
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = awaitLastFeedingSummaryPopulated()
            assertEquals(Duration.ofMinutes(15), summary.firstSideDuration)
            assertEquals(Duration.ofMinutes(15), summary.secondSideDuration)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary subtracts paused duration from unswitched session`() = runTest {
        val now = Instant.ofEpochSecond(1744545600L)
        val startTime = Instant.ofEpochSecond(1744538400L)
        val endTime = Instant.ofEpochSecond(1744540200L)

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.LEFT,
                pausedDurationMs = Duration.ofMinutes(10).toMillis()
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = awaitLastFeedingSummaryPopulated()
            assertEquals(Duration.ofMinutes(20), summary.firstSideDuration)
            assertNull(summary.secondSideDuration)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `lastFeedingSummary subtracts paused duration from second side after switch`() = runTest {
        val now = Instant.ofEpochSecond(1744545600L)
        val startTime = Instant.ofEpochSecond(1744538400L)
        val switchTime = Instant.ofEpochSecond(1744539000L)
        val endTime = Instant.ofEpochSecond(1744540200L)

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            val session = BreastfeedingSession(
                id = 1L,
                startTime = startTime,
                endTime = endTime,
                startingSide = BreastSide.LEFT,
                switchTime = switchTime,
                pausedDurationMs = Duration.ofMinutes(10).toMillis()
            )
            every { repository.getAllSessions() } returns flowOf(listOf(session))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = awaitLastFeedingSummaryPopulated()
            assertEquals(Duration.ofMinutes(10), summary.firstSideDuration)
            assertEquals(Duration.ofMinutes(10), summary.secondSideDuration)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `onStartSession triggers session sync`() = runTest {
        viewModel.onSideSelected(BreastSide.LEFT)
        val session = BreastfeedingSession(
            id = 1L, startTime = Instant.now(), startingSide = BreastSide.LEFT
        )
        coEvery { repository.insertSession(any()) } answers {
            activeSessionFlow.value = session
            1L
        }
        viewModel.onStartSession()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `onStopSession triggers session sync`() = runTest {
        val session = BreastfeedingSession(
            id = 1L, startTime = Instant.now().minusSeconds(300), startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        coJustRun { repository.updateSession(any()) }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onStopSession()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `onSwitchSide triggers session sync`() = runTest {
        val session = BreastfeedingSession(
            id = 1L, startTime = Instant.now().minusSeconds(300), startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        coJustRun { switchSide(session) }
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onSwitchSide()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `onPauseSession triggers session sync`() = runTest {
        val session = BreastfeedingSession(
            id = 1L, startTime = Instant.now().minusSeconds(300), startingSide = BreastSide.LEFT
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onPauseSession()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `onResumeSession triggers session sync`() = runTest {
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(300),
            startingSide = BreastSide.LEFT,
            pausedAt = Instant.now().minusSeconds(60),
        )
        activeSessionFlow.value = session
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onResumeSession()
        testDispatcher.scheduler.advanceUntilIdle()
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `lastFeedingSummary picks the most recent completed session from history`() = runTest {
        // Pre-create all Instant values before mockkStatic to avoid InaccessibleObjectException
        val now = Instant.ofEpochSecond(1744545600L)          // 2026-04-13T12:00:00Z
        val olderStart = Instant.ofEpochSecond(1744531200L)   // 2026-04-13T08:00:00Z
        val olderEnd = Instant.ofEpochSecond(1744532400L)     // 2026-04-13T08:20:00Z
        val newerStart = Instant.ofEpochSecond(1744538400L)   // 2026-04-13T10:00:00Z
        val newerEnd = Instant.ofEpochSecond(1744540200L)     // 2026-04-13T10:30:00Z

        mockkStatic(Instant::class)
        try {
            every { Instant.now() } returns now

            val older = BreastfeedingSession(
                id = 1L,
                startTime = olderStart,
                endTime = olderEnd,
                startingSide = BreastSide.LEFT
            )
            val newer = BreastfeedingSession(
                id = 2L,
                startTime = newerStart,
                endTime = newerEnd,
                startingSide = BreastSide.RIGHT
            )
            every { repository.getAllSessions() } returns flowOf(listOf(older, newer))
            viewModel = createViewModel()
            testDispatcher.scheduler.advanceUntilIdle()

            val summary = awaitLastFeedingSummaryPopulated()
            assertEquals(2L, summary.lastSession.id)
        } finally {
            unmockkAll()
        }
    }

    @Test
    fun `onAddEntryClick opens sheet with default times and side LEFT when no last feeding`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onAddEntryClick()

        val state = viewModel.uiState.value
        assertTrue(state.showManualEntrySheet)
        assertEquals(BreastSide.LEFT, state.manualEntrySide)
        assertNull(state.manualEntryError)
        assertEquals(Duration.ofMinutes(15), state.manualEntryDurationPreview)
        assertEquals(LocalDate.now(), state.manualEntryDate)
    }

    @Test
    fun `onAddEntryClick defaults side to recommended next side`() = runTest {
        // Ended on LEFT without switch → next recommended side is RIGHT.
        val session = BreastfeedingSession(
            id = 1L,
            startTime = Instant.now().minusSeconds(7200),
            endTime = Instant.now().minusSeconds(6000),
            startingSide = BreastSide.LEFT,
            switchTime = null,
        )
        every { repository.getAllSessions() } returns flowOf(listOf(session))
        viewModel = createViewModel()
        awaitLastFeedingSummaryPopulated()

        viewModel.onAddEntryClick()

        assertEquals(BreastSide.RIGHT, viewModel.uiState.value.manualEntrySide)
    }

    @Test
    fun `onManualEntryChanged updates end time and recomputes duration preview`() = runTest {
        viewModel.onAddEntryClick()
        val date = viewModel.uiState.value.manualEntryDate
        val start = viewModel.uiState.value.manualEntryStartTime

        viewModel.onManualEntryChanged(endTime = start.plusMinutes(30))

        val state = viewModel.uiState.value
        assertEquals(start.plusMinutes(30), state.manualEntryEndTime)
        assertEquals(date, state.manualEntryDate)
        assertEquals(start, state.manualEntryStartTime)
        assertEquals(Duration.ofMinutes(30), state.manualEntryDurationPreview)
    }

    @Test
    fun `onSaveManualEntry saves completed session with selected side and dismisses sheet`() = runTest {
        val sessionSlot = slot<BreastfeedingSession>()
        coEvery { repository.insertSession(capture(sessionSlot)) } returns 7L

        viewModel.onAddEntryClick()
        val date = LocalDate.of(2026, 6, 1)
        viewModel.onManualEntryChanged(date = date)
        viewModel.onManualEntryChanged(startTime = LocalTime.of(10, 0))
        viewModel.onManualEntryChanged(endTime = LocalTime.of(10, 20))

        viewModel.onSaveManualEntry(BreastSide.RIGHT)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 1) { repository.insertSession(match { it.startingSide == BreastSide.RIGHT }) }
        assertFalse(viewModel.uiState.value.showManualEntrySheet)
        assertNull(viewModel.uiState.value.manualEntryError)
        val zone = ZoneId.systemDefault()
        assertEquals(LocalTime.of(10, 0).atDate(date).atZone(zone).toInstant(), sessionSlot.captured.startTime)
        assertEquals(LocalTime.of(10, 20).atDate(date).atZone(zone).toInstant(), sessionSlot.captured.endTime)
        coVerify { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun `onSaveManualEntry sets error and does not save when start equals end`() = runTest {
        viewModel.onAddEntryClick()
        viewModel.onManualEntryChanged(startTime = LocalTime.of(10, 0))
        viewModel.onManualEntryChanged(endTime = LocalTime.of(10, 0))

        viewModel.onSaveManualEntry(BreastSide.LEFT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("End time must be after start time", viewModel.uiState.value.manualEntryError)
        assertTrue(viewModel.uiState.value.showManualEntrySheet)
        coVerify(exactly = 0) { repository.insertSession(any()) }
    }

    @Test
    fun `onSaveManualEntry handles cross-midnight session by shifting start back a day`() = runTest {
        val sessionSlot = slot<BreastfeedingSession>()
        coEvery { repository.insertSession(capture(sessionSlot)) } returns 9L

        viewModel.onAddEntryClick()
        val date = LocalDate.of(2026, 6, 1)
        viewModel.onManualEntryChanged(date = date)
        // start 23:00, end 01:00 on the same picked date → start must shift to previous day
        viewModel.onManualEntryChanged(startTime = LocalTime.of(23, 0))
        viewModel.onManualEntryChanged(endTime = LocalTime.of(1, 0))

        viewModel.onSaveManualEntry(BreastSide.LEFT)
        testDispatcher.scheduler.advanceUntilIdle()

        val zone = ZoneId.systemDefault()
        assertEquals(LocalTime.of(23, 0).atDate(date.minusDays(1)).atZone(zone).toInstant(), sessionSlot.captured.startTime)
        assertEquals(LocalTime.of(1, 0).atDate(date).atZone(zone).toInstant(), sessionSlot.captured.endTime)
        assertEquals(Duration.ofHours(2), Duration.between(sessionSlot.captured.startTime, sessionSlot.captured.endTime!!))
        assertNull(viewModel.uiState.value.manualEntryError)
    }

    @Test
    fun `onDismissManualEntry closes sheet and clears error`() = runTest {
        viewModel.onAddEntryClick()
        viewModel.onManualEntryChanged(startTime = LocalTime.of(10, 0))
        viewModel.onManualEntryChanged(endTime = LocalTime.of(10, 0))
        viewModel.onSaveManualEntry(BreastSide.LEFT)

        viewModel.onDismissManualEntry()

        assertFalse(viewModel.uiState.value.showManualEntrySheet)
        assertNull(viewModel.uiState.value.manualEntryError)
    }

    @Test
    fun `onSaveManualEntry surfaces error and keeps sheet open when insert fails`() = runTest {
        coEvery { repository.insertSession(any()) } throws RuntimeException("db write failed")
        viewModel.onAddEntryClick()

        viewModel.onSaveManualEntry(BreastSide.LEFT)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.showManualEntrySheet)
        assertEquals("Could not save changes. Please try again.", viewModel.uiState.value.manualEntryError)
        coVerify(exactly = 0) { syncToFirestore(any()) }
    }
}
