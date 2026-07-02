package com.babytracker.ui.breastfeeding

import android.content.Context
import app.cash.turbine.test
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
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
import com.babytracker.sharing.usecase.SyncedWrite
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
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

@OptIn(ExperimentalCoroutinesApi::class)
class BreastfeedingEditSheetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

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

    private val sampleSession = BreastfeedingSession(
        id = 42L,
        startTime = Instant.parse("2026-05-14T22:00:00Z"),
        endTime = Instant.parse("2026-05-14T22:35:00Z"),
        startingSide = BreastSide.LEFT,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        switchSide = mockk()
        pauseSession = mockk()
        resumeSession = mockk()
        updateSession = mockk()
        repository = mockk()
        feedSettingsRepository = mockk()
        notificationCoordinator = mockk(relaxed = true)
        syncToFirestore = mockk()

        predictNextFeed = mockk()
        every { repository.getAllSessions() } returns flowOf(emptyList())
        every { repository.getActiveSession() } returns MutableStateFlow(null)
        every { feedSettingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)
        every { feedSettingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)
        every { predictNextFeed() } returns flowOf(null)
        coEvery { syncToFirestore(any()) } returns Unit
        appContext = mockk(relaxed = true)
        every { appContext.getString(R.string.error_bf_end_after_start) } returns "End time must be after start time"
        every { appContext.getString(R.string.error_bf_delete) } returns "Could not delete session. Please try again."
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = BreastfeedingViewModel(
        appContext,
        switchSide, pauseSession, resumeSession,
        updateSession, repository, feedSettingsRepository,
        notificationCoordinator, SyncedWrite(syncToFirestore), predictNextFeed,
    )

    @Test
    fun onEditSessionClickOpensSheetWithOriginalValues() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEditSessionClick(sampleSession)

        val sheet = vm.uiState.value.editSheet
        assertNotNull(sheet)
        assertEquals(sampleSession.startTime, sheet!!.editedStart)
        assertEquals(sampleSession.endTime, sheet.editedEnd)
        assertNull(sheet.validationError)
        assertEquals(false, sheet.isDirty)
    }

    @Test
    fun onEditEndChangedToBeforeStartProducesValidationError() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)

        vm.onEditTimeChanged(sampleSession.startTime, sampleSession.startTime.minusSeconds(60))

        val sheet = vm.uiState.value.editSheet!!
        assertEquals("End time must be after start time", sheet.validationError)
        assertEquals(false, sheet.canSave)
    }

    @Test
    fun onEditDismissClearsSheet() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)

        vm.onEditDismiss()

        assertNull(vm.uiState.value.editSheet)
    }

    @Test
    fun onEditSaveCallsUpdateUseCaseAndClosesSheet() = runTest(testDispatcher) {
        coJustRun { updateSession(any(), any(), any()) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        val newEnd = sampleSession.startTime.plusSeconds(2400)
        vm.onEditTimeChanged(sampleSession.startTime, newEnd)

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 1) { updateSession(sampleSession, sampleSession.startTime, newEnd) }
        assertNull(vm.uiState.value.editSheet)
    }

    @Test
    fun onEditSaveDoesNotCallUseCaseWhenValidationErrorPresent() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        vm.onEditTimeChanged(sampleSession.startTime, sampleSession.startTime.minusSeconds(60))

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { updateSession(any(), any(), any()) }
        assertNotNull(vm.uiState.value.editSheet)
    }

    @Test
    fun savingInProgressEndTriggersNotificationCancel() = runTest(testDispatcher) {
        coJustRun { updateSession(any(), any(), any()) }
        val inProgress = sampleSession.copy(endTime = null)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(inProgress)
        val newEnd = inProgress.startTime.plusSeconds(1800)
        vm.onEditTimeChanged(sampleSession.startTime, newEnd)

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 1) { notificationCoordinator.cancelAllSessionNotifications() }
    }

    @Test
    fun onEditSaveTriggersFirestoreSync() = runTest(testDispatcher) {
        coJustRun { updateSession(any(), any(), any()) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        val newEnd = sampleSession.startTime.plusSeconds(2400)
        vm.onEditTimeChanged(sampleSession.startTime, newEnd)

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun onEditSaveDoesNotSyncWhenUpdateFails() = runTest(testDispatcher) {
        coEvery { updateSession(any(), any(), any()) } throws RuntimeException("DB error")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        val newEnd = sampleSession.startTime.plusSeconds(2400)
        vm.onEditTimeChanged(sampleSession.startTime, newEnd)

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncToFirestore(any()) }
        assertNotNull(vm.uiState.value.editSheet)
    }

    @Test
    fun onEditSessionClickFromFeedingScreenOpensSheetWithCorrectSession() = runTest(testDispatcher) {
        val anotherSession = sampleSession.copy(id = 99L, startingSide = BreastSide.RIGHT)
        val vm = viewModel()
        advanceUntilIdle()

        vm.onEditSessionClick(anotherSession)

        val sheet = vm.uiState.value.editSheet
        assertNotNull(sheet)
        assertEquals(99L, sheet!!.original.id)
        assertEquals(BreastSide.RIGHT, sheet.original.startingSide)
    }

    @Test
    fun onPendingDeleteSessionChangedSetsPendingDeleteSession() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.onPendingDeleteSessionChanged(sampleSession)

        assertEquals(sampleSession, vm.uiState.value.pendingDeleteSession)
    }

    @Test
    fun onPendingDeleteSessionChangedNullClearsPendingWithoutDeleting() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPendingDeleteSessionChanged(sampleSession)

        vm.onPendingDeleteSessionChanged(null)
        advanceUntilIdle()

        assertNull(vm.uiState.value.pendingDeleteSession)
        coVerify(exactly = 0) { repository.deleteSession(any()) }
    }

    @Test
    fun onConfirmDeleteSessionDeletesSyncsAndClearsPending() = runTest(testDispatcher) {
        coJustRun { repository.deleteSession(any()) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPendingDeleteSessionChanged(sampleSession)

        vm.onConfirmDeleteSession()
        advanceUntilIdle()

        coVerify(exactly = 1) { repository.deleteSession(sampleSession) }
        coVerify(exactly = 1) { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
        assertNull(vm.uiState.value.pendingDeleteSession)
    }

    @Test
    fun onConfirmDeleteSessionForInProgressCancelsNotifications() = runTest(testDispatcher) {
        coJustRun { repository.deleteSession(any()) }
        val inProgress = sampleSession.copy(endTime = null)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPendingDeleteSessionChanged(inProgress)

        vm.onConfirmDeleteSession()
        advanceUntilIdle()

        coVerify(exactly = 1) { notificationCoordinator.cancelAllSessionNotifications() }
    }

    @Test
    fun onConfirmDeleteSessionSetsErrorAndSkipsSyncWhenDeleteFails() = runTest(testDispatcher) {
        coEvery { repository.deleteSession(any()) } throws RuntimeException("DB error")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onPendingDeleteSessionChanged(sampleSession)

        vm.onConfirmDeleteSession()
        advanceUntilIdle()

        assertEquals("Could not delete session. Please try again.", vm.uiState.value.error)
        coVerify(exactly = 0) { syncToFirestore(any()) }
    }
}
