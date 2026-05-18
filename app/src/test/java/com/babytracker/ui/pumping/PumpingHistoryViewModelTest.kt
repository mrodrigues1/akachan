package com.babytracker.ui.pumping

import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import com.babytracker.domain.usecase.pumping.DeletePumpingSessionUseCase
import com.babytracker.domain.usecase.pumping.GetPumpingHistoryUseCase
import com.babytracker.domain.usecase.pumping.UpdatePumpingSessionUseCase
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
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
class PumpingHistoryViewModelTest {

    private lateinit var getHistory: GetPumpingHistoryUseCase
    private lateinit var updateSession: UpdatePumpingSessionUseCase
    private lateinit var deleteSession: DeletePumpingSessionUseCase
    private lateinit var viewModel: PumpingHistoryViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val historyFlow = MutableStateFlow<List<PumpingSession>>(emptyList())
    private val fixedNow = Instant.ofEpochSecond(1_700_000_000L)

    private val sampleSession = PumpingSession(
        id = 1L,
        startTime = fixedNow.minusSeconds(600),
        endTime = fixedNow,
        breast = PumpingBreast.LEFT,
        volumeMl = 120,
        notes = "Morning session",
    )

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        getHistory = mockk()
        updateSession = mockk()
        deleteSession = mockk()
        every { getHistory() } returns historyFlow
        viewModel = PumpingHistoryViewModel(
            getHistory = getHistory,
            updateSession = updateSession,
            deleteSession = deleteSession,
            now = { fixedNow },
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `sessions flow emits list from repository`() = runTest {
        historyFlow.value = listOf(sampleSession)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf(sampleSession), viewModel.uiState.value.sessions)
    }

    @Test
    fun `onEditClicked opens sheet prefilled with session values`() = runTest {
        viewModel.onEditClicked(sampleSession)

        val sheet = viewModel.uiState.value.editSheet
        assertNotNull(sheet)
        assertEquals(sampleSession, sheet!!.original)
        assertEquals(sampleSession.startTime, sheet.editedStart)
        assertEquals(sampleSession.endTime, sheet.editedEnd)
        assertEquals(sampleSession.breast, sheet.editedBreast)
        assertEquals("120", sheet.editedVolumeMl)
        assertEquals("Morning session", sheet.editedNotes)
    }

    @Test
    fun `onEditClicked prefills empty string when volumeMl is null`() = runTest {
        val sessionNoVolume = sampleSession.copy(volumeMl = null, notes = null)
        viewModel.onEditClicked(sessionNoVolume)

        val sheet = viewModel.uiState.value.editSheet!!
        assertEquals("", sheet.editedVolumeMl)
        assertEquals("", sheet.editedNotes)
    }

    @Test
    fun `onEditFieldChange populates validationError when end before start`() = runTest {
        viewModel.onEditClicked(sampleSession)

        viewModel.onEditFieldChange { it.copy(editedEnd = it.editedStart.minusSeconds(60)) }

        val sheet = viewModel.uiState.value.editSheet!!
        assertNotNull(sheet.validationError)
    }

    @Test
    fun `onEditFieldChange clears error when fields become valid`() = runTest {
        viewModel.onEditClicked(sampleSession)
        viewModel.onEditFieldChange { it.copy(editedEnd = it.editedStart.minusSeconds(60)) }
        assertNotNull(viewModel.uiState.value.editSheet!!.validationError)

        viewModel.onEditFieldChange { it.copy(editedEnd = it.editedStart.plusSeconds(120)) }

        assertNull(viewModel.uiState.value.editSheet!!.validationError)
    }

    @Test
    fun `onEditFieldChange populates validationError when volume is zero`() = runTest {
        viewModel.onEditClicked(sampleSession)

        viewModel.onEditFieldChange { it.copy(editedVolumeMl = "0") }

        assertNotNull(viewModel.uiState.value.editSheet!!.validationError)
    }

    @Test
    fun `onEditSave calls updateSession and clears sheet on success`() = runTest {
        viewModel.onEditClicked(sampleSession)
        val sheet = viewModel.uiState.value.editSheet!!
        val updatedSession = sampleSession.copy(volumeMl = 150)
        viewModel.onEditFieldChange { it.copy(editedVolumeMl = "150") }

        val updatedSheet = viewModel.uiState.value.editSheet!!
        coEvery {
            updateSession(
                original = sampleSession,
                startTime = updatedSheet.editedStart,
                endTime = updatedSheet.editedEnd,
                breast = updatedSheet.editedBreast,
                volumeMl = 150,
                notes = updatedSheet.editedNotes.ifBlank { null },
            )
        } returns updatedSession

        viewModel.onEditSave()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.editSheet)
        coVerify(exactly = 1) {
            updateSession(
                original = sampleSession,
                startTime = any(),
                endTime = any(),
                breast = any(),
                volumeMl = 150,
                notes = any(),
            )
        }
    }

    @Test
    fun `onEditSave does nothing when sheet has validation error`() = runTest {
        viewModel.onEditClicked(sampleSession)
        viewModel.onEditFieldChange { it.copy(editedEnd = it.editedStart.minusSeconds(60)) }
        assertNotNull(viewModel.uiState.value.editSheet!!.validationError)

        viewModel.onEditSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateSession(any(), any(), any(), any(), any(), any()) }
        assertNotNull(viewModel.uiState.value.editSheet)
    }

    @Test
    fun `onEditSave does nothing when sheet is not dirty`() = runTest {
        viewModel.onEditClicked(sampleSession)

        viewModel.onEditSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateSession(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `onDeleteConfirmed calls deleteSession and clears sheet on success`() = runTest {
        viewModel.onEditClicked(sampleSession)
        coJustRun { deleteSession(sampleSession) }

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.editSheet)
        coVerify(exactly = 1) { deleteSession(sampleSession) }
    }

    @Test
    fun `onDeleteCancelled hides confirm row without deleting`() = runTest {
        viewModel.onEditClicked(sampleSession)

        viewModel.onDeleteRequested()
        assertTrue(viewModel.uiState.value.editSheet!!.deleteConfirm)

        viewModel.onDeleteCancelled()

        assertTrue(!viewModel.uiState.value.editSheet!!.deleteConfirm)
        coVerify(exactly = 0) { deleteSession(any()) }
    }

    @Test
    fun `onEditDismiss clears sheet`() = runTest {
        viewModel.onEditClicked(sampleSession)
        assertNotNull(viewModel.uiState.value.editSheet)

        viewModel.onEditDismiss()

        assertNull(viewModel.uiState.value.editSheet)
    }

    @Test
    fun `onDeleteConfirmed sets error and keeps sheet when deleteSession throws`() = runTest {
        viewModel.onEditClicked(sampleSession)
        coEvery { deleteSession(sampleSession) } throws RuntimeException("db error")

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.editSheet)
        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `onErrorDismissed clears error`() = runTest {
        viewModel.onEditClicked(sampleSession)
        coEvery { deleteSession(sampleSession) } throws RuntimeException("db error")
        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.error)

        viewModel.onErrorDismissed()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `onEditFieldChange rejects future startTime for in-progress session`() = runTest {
        val activeSession = sampleSession.copy(endTime = null)
        viewModel.onEditClicked(activeSession)

        viewModel.onEditFieldChange { it.copy(editedStart = fixedNow.plusSeconds(60)) }

        assertNotNull(viewModel.uiState.value.editSheet!!.validationError)
        assertEquals("Start cannot be in the future", viewModel.uiState.value.editSheet!!.validationError)
    }

    @Test
    fun `onEditSave blocked when startTime is in the future`() = runTest {
        val activeSession = sampleSession.copy(endTime = null, volumeMl = 50)
        viewModel.onEditClicked(activeSession)
        viewModel.onEditFieldChange { it.copy(editedStart = fixedNow.plusSeconds(60)) }
        assertNotNull(viewModel.uiState.value.editSheet!!.validationError)

        viewModel.onEditSave()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { updateSession(any(), any(), any(), any(), any(), any()) }
    }
}
