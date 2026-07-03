package com.babytracker.ui.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.ToggleVisitQuestionAnsweredUseCase
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class VisitQuestionsViewModelTest {
    private val repository = mockk<DoctorVisitRepository>(relaxed = true)
    private val add = mockk<AddVisitQuestionUseCase>(relaxed = true)
    private val toggle = mockk<ToggleVisitQuestionAnsweredUseCase>(relaxed = true)

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    @Test
    fun `add clears draft and calls use case`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = VisitQuestionsViewModel(repository, add, toggle)
        vm.onDraftChange("New Q")
        vm.onAdd()
        advanceUntilIdle()
        coVerify { add("New Q", any()) }
    }

    @Test
    fun `blank draft is ignored`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        val vm = VisitQuestionsViewModel(repository, add, toggle)
        vm.onDraftChange("   ")
        vm.onAdd()
        advanceUntilIdle()
        coVerify(exactly = 0) { add(any(), any()) }
    }

    @Test
    fun `rapid double add enqueues the question only once`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = VisitQuestionsViewModel(repository, add, toggle)
        vm.onDraftChange("Only once")
        // Two taps before the suspending write runs: the first must clear the draft synchronously
        // so the second reads an empty draft and is ignored.
        vm.onAdd()
        vm.onAdd()
        advanceUntilIdle()
        coVerify(exactly = 1) { add("Only once", any()) }
    }

    @Test
    fun `add trims surrounding whitespace`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = VisitQuestionsViewModel(repository, add, toggle)
        vm.onDraftChange("  Spaced out  ")
        vm.onAdd()
        advanceUntilIdle()
        coVerify { add("Spaced out", any()) }
    }

    @Test
    fun `delete then undo re-adds`() = runTest {
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = VisitQuestionsViewModel(repository, add, toggle)
        val q = VisitQuestion(id = 5, text = "Ask about sleep", createdAt = Instant.EPOCH)
        vm.onDelete(q)
        advanceUntilIdle()
        coVerify { repository.deleteQuestionById(5) }
        vm.onUndoDelete()
        advanceUntilIdle()
        coVerify { add("Ask about sleep", any()) }
    }
}
