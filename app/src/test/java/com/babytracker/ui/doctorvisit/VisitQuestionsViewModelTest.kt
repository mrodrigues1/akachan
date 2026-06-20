package com.babytracker.ui.doctorvisit

import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.DeleteVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveInboxQuestionsUseCase
import com.babytracker.domain.usecase.doctorvisit.ToggleVisitQuestionAnsweredUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class VisitQuestionsViewModelTest {
    private val observeInbox = mockk<ObserveInboxQuestionsUseCase>()
    private val add = mockk<AddVisitQuestionUseCase>(relaxed = true)
    private val toggle = mockk<ToggleVisitQuestionAnsweredUseCase>(relaxed = true)
    private val delete = mockk<DeleteVisitQuestionUseCase>(relaxed = true)

    @BeforeEach
    fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `add clears draft and calls use case`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = VisitQuestionsViewModel(observeInbox, add, toggle, delete)
        vm.onDraftChange("New Q")
        vm.onAdd()
        advanceUntilIdle()
        coVerify { add("New Q", any()) }
    }

    @Test
    fun `blank draft is ignored`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        val vm = VisitQuestionsViewModel(observeInbox, add, toggle, delete)
        vm.onDraftChange("   ")
        vm.onAdd()
        advanceUntilIdle()
        coVerify(exactly = 0) { add(any(), any()) }
    }

    @Test
    fun `delete then undo re-adds`() = runTest {
        every { observeInbox() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = VisitQuestionsViewModel(observeInbox, add, toggle, delete)
        val q = VisitQuestion(id = 5, text = "Ask about sleep", createdAt = Instant.EPOCH)
        vm.onDelete(q)
        advanceUntilIdle()
        coVerify { delete(5) }
        vm.onUndoDelete()
        advanceUntilIdle()
        coVerify { add("Ask about sleep", any()) }
    }
}
