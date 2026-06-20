package com.babytracker.ui.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.AddDoctorVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.AttachSnapshotToVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.EditDoctorVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.GenerateVisitSnapshotUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveInboxQuestionsUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveVisitQuestionsUseCase
import com.babytracker.export.data.BackupFileWriter
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorVisitViewModelTest {
    private val observeInbox = mockk<ObserveInboxQuestionsUseCase>()
    private val observeVisitQuestions = mockk<ObserveVisitQuestionsUseCase>()
    private val repository = mockk<DoctorVisitRepository>(relaxed = true)
    private val addVisit = mockk<AddDoctorVisitUseCase>(relaxed = true)
    private val editVisit = mockk<EditDoctorVisitUseCase>(relaxed = true)
    private val attachSnapshot = mockk<AttachSnapshotToVisitUseCase>(relaxed = true)
    private val generateSnapshot = mockk<GenerateVisitSnapshotUseCase>(relaxed = true)
    private val fileWriter = mockk<BackupFileWriter>(relaxed = true)

    @BeforeEach
    fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DoctorVisitViewModel(
        observeInbox, observeVisitQuestions, repository,
        addVisit, editVisit, attachSnapshot, generateSnapshot, fileWriter,
    )

    @Test
    fun `add path calls addVisit with selected ids and blanks-allowed fields`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        every { observeVisitQuestions(any()) } returns flowOf(emptyList())
        val vm = vm()
        vm.onProviderChange("Dr A")
        vm.onNotesChange("notes")
        vm.onDateChange(Instant.ofEpochMilli(5_000))
        vm.onToggleQuestion(1)
        vm.onToggleQuestion(2)
        vm.onSave()
        advanceUntilIdle()
        coVerify {
            addVisit(
                Instant.ofEpochMilli(5_000),
                "Dr A",
                "notes",
                match { it.toSet() == setOf(1L, 2L) },
                any(),
            )
        }
    }

    @Test
    fun `edit path preserves original createdAt and snapshotCreatedAt`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        every { observeVisitQuestions(any()) } returns flowOf(emptyList())
        val vm = vm()
        val visit = DoctorVisit(
            id = 7,
            date = Instant.ofEpochMilli(9_000),
            snapshotLabel = "snap",
            snapshotCreatedAt = Instant.ofEpochMilli(500),
            createdAt = Instant.ofEpochMilli(1_000),
        )
        vm.startEdit(visit)
        advanceUntilIdle()
        vm.onSave()
        advanceUntilIdle()
        val captured = slot<DoctorVisit>()
        coVerify { editVisit(capture(captured), any()) }
        assertEquals(7L, captured.captured.id)
        assertEquals(Instant.ofEpochMilli(1_000), captured.captured.createdAt)
        assertEquals(Instant.ofEpochMilli(500), captured.captured.snapshotCreatedAt)
    }

    @Test
    fun `loadForEdit seeds selection and surfaces attached questions`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        every { observeVisitQuestions(7) } returns flowOf(
            listOf(
                VisitQuestion(id = 3, text = "a", visitId = 7, createdAt = Instant.EPOCH),
                VisitQuestion(id = 4, text = "b", visitId = 7, createdAt = Instant.EPOCH),
            ),
        )
        coEvery { repository.getVisitById(7) } returns DoctorVisit(
            id = 7, date = Instant.ofEpochMilli(9_000), createdAt = Instant.ofEpochMilli(1_000),
        )
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.loadForEdit(7)
        advanceUntilIdle()
        assertEquals(setOf(3L, 4L), vm.uiState.value.selectedQuestionIds)
        assertEquals(2, vm.uiState.value.attachedQuestions.size)
    }

    @Test
    fun `toggle adds then removes a question id`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        every { observeVisitQuestions(any()) } returns flowOf(emptyList())
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onToggleQuestion(5)
        advanceUntilIdle()
        assertEquals(setOf(5L), vm.uiState.value.selectedQuestionIds)
        vm.onToggleQuestion(5)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.selectedQuestionIds.isEmpty())
    }

    @Test
    fun `saved flag set on save and cleared on consume`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        every { observeVisitQuestions(any()) } returns flowOf(emptyList())
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onSave()
        advanceUntilIdle()
        assertTrue(vm.uiState.value.saved)
        vm.onSavedConsumed()
        advanceUntilIdle()
        assertFalse(vm.uiState.value.saved)
    }
}
