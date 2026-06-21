package com.babytracker.ui.doctorvisit

import app.cash.turbine.test
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.VisitQuestion
import com.babytracker.domain.usecase.doctorvisit.AddVisitQuestionUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveDoctorVisitsUseCase
import com.babytracker.domain.usecase.doctorvisit.ObserveInboxQuestionsUseCase
import com.babytracker.domain.usecase.doctorvisit.ToggleVisitQuestionAnsweredUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorVisitDashboardViewModelTest {
    private val observeVisits = mockk<ObserveDoctorVisitsUseCase>()
    private val observeInbox = mockk<ObserveInboxQuestionsUseCase>()
    private val add = mockk<AddVisitQuestionUseCase>(relaxed = true)
    private val toggle = mockk<ToggleVisitQuestionAnsweredUseCase>(relaxed = true)

    private val nowInstant = Instant.parse("2026-06-21T12:00:00Z")
    private val now: () -> Instant = { nowInstant }

    @BeforeEach
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

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
    fun `derives next visit, recent visits, and open questions`() = runTest {
        every { observeVisits() } returns flowOf(
            listOf(
                visit(1, offsetDays = 3, provider = "Dr. Silva"),
                visit(2, offsetDays = -2, provider = "Dr. Costa"),
                visit(3, offsetDays = -10),
            ),
        )
        every { observeInbox() } returns flowOf(
            listOf(
                question(10, "Is the rash normal?"),
                question(11, "Tummy time?", answered = true),
                question(12, "Vitamin D dose?"),
            ),
        )
        val vm = DoctorVisitDashboardViewModel(observeVisits, observeInbox, add, toggle, now)

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertEquals(1L, state.nextVisit?.id)
            assertEquals(listOf(2L, 3L), state.recentVisits.map { it.id })
            assertEquals(listOf(10L, 12L), state.questions.map { it.id })
            assertEquals(2, state.openQuestionCount)
            assertTrue(!state.isFirstRun)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first run flag set when nothing recorded`() = runTest {
        every { observeVisits() } returns flowOf(emptyList())
        every { observeInbox() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(observeVisits, observeInbox, add, toggle, now)

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
        every { observeVisits() } returns flowOf(emptyList())
        every { observeInbox() } returns flowOf(emptyList())
        coEvery { add(any(), any()) } returns 1
        val vm = DoctorVisitDashboardViewModel(observeVisits, observeInbox, add, toggle, now)

        vm.onDraftChange("  Ask about sleep  ")
        vm.onAddQuestion()
        vm.onAddQuestion() // draft cleared synchronously, so this repeat tap is a no-op
        advanceUntilIdle()

        coVerify(exactly = 1) { add("Ask about sleep", any()) }
    }

    @Test
    fun `blank draft is ignored`() = runTest {
        every { observeVisits() } returns flowOf(emptyList())
        every { observeInbox() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(observeVisits, observeInbox, add, toggle, now)

        vm.onDraftChange("   ")
        vm.onAddQuestion()
        advanceUntilIdle()

        coVerify(exactly = 0) { add(any(), any()) }
    }

    @Test
    fun `onToggleAnswered delegates to use case`() = runTest {
        every { observeVisits() } returns flowOf(emptyList())
        every { observeInbox() } returns flowOf(emptyList())
        val vm = DoctorVisitDashboardViewModel(observeVisits, observeInbox, add, toggle, now)

        vm.onToggleAnswered(7)
        advanceUntilIdle()

        coVerify { toggle(7) }
    }
}
