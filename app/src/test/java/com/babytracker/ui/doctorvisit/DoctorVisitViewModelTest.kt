package com.babytracker.ui.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.AddDoctorVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.AttachSnapshotToVisitUseCase
import com.babytracker.domain.usecase.doctorvisit.EditDoctorVisitUseCase
import com.babytracker.export.data.BackupFileWriter
import com.babytracker.export.domain.usecase.GeneratePdfReportUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    private val repository = mockk<DoctorVisitRepository>(relaxed = true)
    private val addQuestion = mockk<AddVisitQuestionUseCase>(relaxed = true)
    private val addVisit = mockk<AddDoctorVisitUseCase>(relaxed = true)
    private val editVisit = mockk<EditDoctorVisitUseCase>(relaxed = true)
    private val attachSnapshot = mockk<AttachSnapshotToVisitUseCase>(relaxed = true)
    private val generatePdfReport = mockk<GeneratePdfReportUseCase>(relaxed = true)
    private val fileWriter = mockk<BackupFileWriter>(relaxed = true)

    @BeforeEach
    fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DoctorVisitViewModel(
        repository, addQuestion,
        addVisit, editVisit, attachSnapshot, generatePdfReport, fileWriter,
    )

    @Test
    fun `adding a question clears the draft and selects the inserted question`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        every { repository.observeQuestionsForVisit(any()) } returns flowOf(emptyList())
        coEvery { addQuestion("Question text", any()) } returns 42
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onQuestionDraftChange("  Question text  ")
        vm.onAddQuestion()
        advanceUntilIdle()

        assertEquals("", vm.uiState.value.questionDraft)
        assertEquals(setOf(42L), vm.uiState.value.selectedQuestionIds)
    }

    @Test
    fun `adding a blank question does nothing`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        every { repository.observeQuestionsForVisit(any()) } returns flowOf(emptyList())
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onQuestionDraftChange("   ")
        vm.onAddQuestion()
        advanceUntilIdle()

        coVerify(exactly = 0) { addQuestion(any(), any()) }
        assertTrue(vm.uiState.value.selectedQuestionIds.isEmpty())
    }

    @Test
    fun `save waits for a pending question before attaching selections`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        every { repository.observeQuestionsForVisit(any()) } returns flowOf(emptyList())
        val insertedId = CompletableDeferred<Long>()
        coEvery { addQuestion("Question", any()) } coAnswers { insertedId.await() }
        val vm = vm()

        vm.onQuestionDraftChange("Question")
        vm.onAddQuestion()
        vm.onSave()
        runCurrent()

        coVerify(exactly = 0) { addVisit(any(), any(), any(), any(), any()) }
        insertedId.complete(42)
        advanceUntilIdle()
        coVerify {
            addVisit(any(), any(), any(), match { 42L in it }, any())
        }
    }

    @Test
    fun `add path calls addVisit with selected ids and blanks-allowed fields`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        every { repository.observeQuestionsForVisit(any()) } returns flowOf(emptyList())
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
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        every { repository.observeQuestionsForVisit(any()) } returns flowOf(emptyList())
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
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        every { repository.observeQuestionsForVisit(7) } returns flowOf(
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
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        every { repository.observeQuestionsForVisit(any()) } returns flowOf(emptyList())
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
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        every { repository.observeQuestionsForVisit(any()) } returns flowOf(emptyList())
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
