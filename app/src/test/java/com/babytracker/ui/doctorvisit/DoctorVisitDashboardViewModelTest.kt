package com.babytracker.ui.doctorvisit

import app.cash.turbine.test
import com.babytracker.domain.model.DoctorVisit
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorVisitDashboardViewModelTest {
    private val repository = mockk<DoctorVisitRepository>()
    private val add = mockk<AddVisitQuestionUseCase>(relaxed = true)
    private val toggle = mockk<ToggleVisitQuestionAnsweredUseCase>(relaxed = true)

    private val nowInstant = Instant.parse("2026-06-21T12:00:00Z")
    private val now: () -> Instant = { nowInstant }

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    private fun visit(id: Long, offsetDays: Long, provider: String? = null) =
        DoctorVisit(
            id = id,
            date = nowInstant.plusSeconds(offsetDays * 86_400),
            providerName = provider,
            createdAt = Instant.EPOCH,
        )

    private fun question(id: Long, text: String, answered: Boolean = false) =
        VisitQuestion(id = id, text = text, answered = answered, createdAt = Instant.EPOCH)

    @Test
    fun `derives next visit, remaining upcoming, recent visits, and open questions`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(
            listOf(
                visit(1, offsetDays = 3, provider = "Dr. Silva"),
                visit(2, offsetDays = -2, provider = "Dr. Costa"),
                visit(3, offsetDays = -10),
                visit(4, offsetDays = 20, provider = "Dr. Lima"),
                visit(5, offsetDays = 10, provider = "Dr. Reis"),
            ),
        )
        every { repository.observeInboxQuestions() } returns flowOf(
            listOf(
                question(10, "Is the rash normal?"),
                question(11, "Tummy time?", answered = true),
                question(12, "Vitamin D dose?"),
            ),
        )
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertEquals(1L, state.nextVisit?.id)
            assertEquals(3, state.nextVisitInDays)
            // Soonest first, hero's nearest visit dropped.
            assertEquals(listOf(5L, 4L), state.upcomingVisits.map { it.id })
            assertEquals(listOf(2L, 3L), state.recentVisits.map { it.id })
            assertEquals(listOf(10L, 12L), state.questions.map { it.id })
            assertEquals(2, state.openQuestionCount)
            assertTrue(!state.isFirstRun)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `exposes every open question without capping the preview`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeInboxQuestions() } returns flowOf(
            (1L..8L).map { question(it, "Question $it") },
        )
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertEquals((1L..8L).toList(), state.questions.map { it.id })
            assertEquals(8, state.openQuestionCount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `upcoming list is empty when only one visit is scheduled`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(listOf(visit(1, offsetDays = 3)))
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertEquals(1L, state.nextVisit?.id)
            assertTrue(state.upcomingVisits.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first run flag set when nothing recorded`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertNull(state.nextVisit)
            assertTrue(state.recentVisits.isEmpty())
            assertTrue(state.isFirstRun)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onAddQuestion trims, clears draft, and ignores the immediate repeat tap`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.onDraftChange("  Ask about sleep  ")
        vm.onAddQuestion()
        vm.onAddQuestion() // draft cleared synchronously, so this repeat tap is a no-op
        advanceUntilIdle()

        coVerify(exactly = 1) { add("Ask about sleep", any()) }
    }

    @Test
    fun `blank draft is ignored`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.onDraftChange("   ")
        vm.onAddQuestion()
        advanceUntilIdle()

        coVerify(exactly = 0) { add(any(), any()) }
    }

    @Test
    fun `onToggleAnswered delegates to use case`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.onToggleAnswered(7)
        advanceUntilIdle()

        coVerify { toggle(7) }
    }

    @Test
    fun `onToggleAnswered captures the question and undo flips it back`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeInboxQuestions() } returns flowOf(listOf(question(10, "Is the rash normal?")))
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertEquals(listOf(10L), state.questions.map { it.id })

            vm.onToggleAnswered(10)
            state = awaitItem()
            assertEquals(10L, state.lastAnswered?.id)

            vm.onUndoAnswered()
            state = awaitItem()
            assertNull(state.lastAnswered)
            cancelAndIgnoreRemainingEvents()
        }

        // Once to mark answered, once to flip back on undo.
        coVerify(exactly = 2) { toggle(10) }
    }

    @Test
    fun `consuming the snackbar clears lastAnswered without re-toggling`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeInboxQuestions() } returns flowOf(listOf(question(10, "Is the rash normal?")))
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()

            vm.onToggleAnswered(10)
            state = awaitItem()
            assertEquals(10L, state.lastAnswered?.id)

            vm.onUndoAnsweredConsumed()
            state = awaitItem()
            assertNull(state.lastAnswered)
            cancelAndIgnoreRemainingEvents()
        }

        // Dismissal must not re-toggle: only the initial mark hits the use case.
        coVerify(exactly = 1) { toggle(10) }
    }

    @Test
    fun `surfaces an error state when a source flow throws`() = runTest {
        every { repository.observeAllVisits() } returns flow { throw IllegalStateException("boom") }
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertTrue(state.isError)
            assertTrue(!state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onRetry rebuilds the flow and clears the error`() = runTest {
        every { repository.observeAllVisits() } returnsMany listOf(
            flow { throw IllegalStateException("boom") },
            flowOf(emptyList()),
        )
        every { repository.observeInboxQuestions() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(repository, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertTrue(state.isError)

            vm.onRetry()
            state = awaitItem()
            assertTrue(!state.isError)
            assertTrue(!state.isLoading)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
