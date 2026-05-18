package com.babytracker.ui.breastfeeding

import app.cash.turbine.test
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SettingsRepository
import com.babytracker.domain.usecase.breastfeeding.DeleteBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.GetBreastfeedingHistoryUseCase
import com.babytracker.domain.usecase.breastfeeding.PauseBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.ResumeBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StartBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.StopBreastfeedingSessionUseCase
import com.babytracker.domain.usecase.breastfeeding.SwitchBreastfeedingSideUseCase
import com.babytracker.domain.usecase.breastfeeding.UpdateBreastfeedingSessionUseCase
import com.babytracker.manager.BreastfeedingSessionNotificationCoordinator
import com.babytracker.sharing.usecase.SyncToFirestoreUseCase
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class BreastfeedingEditSheetViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var startSession: StartBreastfeedingSessionUseCase
    private lateinit var stopSession: StopBreastfeedingSessionUseCase
    private lateinit var switchSide: SwitchBreastfeedingSideUseCase
    private lateinit var getHistory: GetBreastfeedingHistoryUseCase
    private lateinit var pauseSession: PauseBreastfeedingSessionUseCase
    private lateinit var resumeSession: ResumeBreastfeedingSessionUseCase
    private lateinit var updateSession: UpdateBreastfeedingSessionUseCase
    private lateinit var deleteSession: DeleteBreastfeedingSessionUseCase
    private lateinit var repository: BreastfeedingRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationCoordinator: BreastfeedingSessionNotificationCoordinator
    private lateinit var syncToFirestore: SyncToFirestoreUseCase

    private val sampleSession = BreastfeedingSession(
        id = 42L,
        startTime = Instant.parse("2026-05-14T22:00:00Z"),
        endTime = Instant.parse("2026-05-14T22:35:00Z"),
        startingSide = BreastSide.LEFT,
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        startSession = mockk()
        stopSession = mockk()
        switchSide = mockk()
        pauseSession = mockk()
        resumeSession = mockk()
        updateSession = mockk()
        deleteSession = mockk()
        repository = mockk()
        settingsRepository = mockk()
        notificationCoordinator = mockk(relaxed = true)
        syncToFirestore = mockk()

        getHistory = mockk()
        every { getHistory() } returns flowOf(emptyList())
        every { repository.getActiveSession() } returns MutableStateFlow(null)
        every { settingsRepository.getMaxPerBreastMinutes() } returns flowOf(0)
        every { settingsRepository.getMaxTotalFeedMinutes() } returns flowOf(0)
        coEvery { syncToFirestore(any()) } returns Unit
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = BreastfeedingViewModel(
        startSession, stopSession, switchSide, getHistory, pauseSession, resumeSession,
        updateSession, deleteSession, repository, settingsRepository,
        notificationCoordinator, syncToFirestore,
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

        vm.onEditEndChanged(sampleSession.startTime.minusSeconds(60))

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
        vm.onEditEndChanged(newEnd)

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
        vm.onEditEndChanged(sampleSession.startTime.minusSeconds(60))

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { updateSession(any(), any(), any()) }
        assertNotNull(vm.uiState.value.editSheet)
    }

    @Test
    fun onDeleteRequestedShowsConfirm() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)

        vm.onDeleteRequested()

        assertTrue(vm.uiState.value.editSheet!!.deleteConfirm)
    }

    @Test
    fun onDeleteCancelledHidesConfirmKeepsSheet() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        vm.onDeleteRequested()

        vm.onDeleteCancelled()

        val sheet = vm.uiState.value.editSheet!!
        assertEquals(false, sheet.deleteConfirm)
    }

    @Test
    fun onDeleteConfirmedCallsDeleteAndClosesSheet() = runTest(testDispatcher) {
        coJustRun { deleteSession(any()) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        vm.onDeleteRequested()

        vm.onDeleteConfirmed()
        advanceUntilIdle()

        coVerify(exactly = 1) { deleteSession(sampleSession) }
        assertNull(vm.uiState.value.editSheet)
    }

    @Test
    fun savingInProgressEndTriggersNotificationCancel() = runTest(testDispatcher) {
        coJustRun { updateSession(any(), any(), any()) }
        val inProgress = sampleSession.copy(endTime = null)
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(inProgress)
        val newEnd = inProgress.startTime.plusSeconds(1800)
        vm.onEditEndChanged(newEnd)

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
        vm.onEditEndChanged(newEnd)

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
        vm.onEditEndChanged(newEnd)

        vm.onEditSave()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncToFirestore(any()) }
        assertNotNull(vm.uiState.value.editSheet)
    }

    @Test
    fun onDeleteConfirmedTriggersFirestoreSync() = runTest(testDispatcher) {
        coJustRun { deleteSession(any()) }
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        vm.onDeleteRequested()

        vm.onDeleteConfirmed()
        advanceUntilIdle()

        coVerify(exactly = 1) { syncToFirestore(SyncToFirestoreUseCase.SyncType.SESSIONS) }
    }

    @Test
    fun onDeleteConfirmedDoesNotSyncWhenDeleteFails() = runTest(testDispatcher) {
        coEvery { deleteSession(any()) } throws RuntimeException("DB error")
        val vm = viewModel()
        advanceUntilIdle()
        vm.onEditSessionClick(sampleSession)
        vm.onDeleteRequested()

        vm.onDeleteConfirmed()
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
}
