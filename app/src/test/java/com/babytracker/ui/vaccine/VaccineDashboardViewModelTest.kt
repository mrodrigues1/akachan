package com.babytracker.ui.vaccine

import app.cash.turbine.test
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineRecordsUseCase
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
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
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class VaccineDashboardViewModelTest {
    private val observeRecords = mockk<ObserveVaccineRecordsUseCase>()
    private val markGiven = mockk<MarkVaccineAdministeredUseCase>(relaxed = true)

    private val zone = ZoneId.of("UTC")
    private val nowInstant = Instant.parse("2026-06-21T12:00:00Z")
    private val now: () -> Instant = { nowInstant }

    @BeforeEach
    fun setup() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun scheduled(id: Long, offsetDays: Long) = VaccineRecord(
        id = id,
        name = "Vaccine $id",
        status = VaccineStatus.SCHEDULED,
        scheduledDate = nowInstant.plusSeconds(offsetDays * 86_400),
        createdAt = Instant.EPOCH,
    )

    private fun administered(id: Long, offsetDays: Long) = VaccineRecord(
        id = id,
        name = "Vaccine $id",
        status = VaccineStatus.ADMINISTERED,
        administeredDate = nowInstant.plusSeconds(offsetDays * 86_400),
        createdAt = Instant.EPOCH,
    )

    private fun viewModel() = VaccineDashboardViewModel(observeRecords, markGiven, zone, now)

    @Test
    fun `derives overdue hero, schedule order, and recently given`() = runTest {
        every { observeRecords() } returns flowOf(
            listOf(
                scheduled(1, offsetDays = 7),
                scheduled(2, offsetDays = -3),
                scheduled(3, offsetDays = -1),
                scheduled(4, offsetDays = 14),
                administered(5, offsetDays = -2),
                administered(6, offsetDays = -10),
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()

            // Overdue first (earliest), then future ascending.
            assertEquals(listOf(2L, 3L, 1L, 4L), state.schedule.map { it.id })
            assertEquals(2L, state.mostOverdue?.id)
            assertEquals(3, state.mostOverdueDays)
            assertEquals(2, state.overdueCount)
            assertEquals(1L, state.nextVaccine?.id)
            assertEquals(7, state.nextInDays)
            assertEquals(listOf(5L, 6L), state.recentlyGiven.map { it.id })
            assertEquals(2, state.givenCount)
            assertTrue(!state.isFirstRun)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first run flag set when nothing recorded`() = runTest {
        every { observeRecords() } returns flowOf(emptyList())
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertTrue(state.isFirstRun)
            assertNull(state.mostOverdue)
            assertNull(state.nextVaccine)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markGiven defers the write, hides the row, and shows it as given optimistically`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { observeRecords() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertEquals(listOf(1L), state.schedule.map { it.id })

            vm.markGiven(record)
            state = awaitItem()
            assertEquals(1L, state.lastMarkedGiven?.id)
            assertTrue(state.schedule.isEmpty())
            assertEquals(listOf(1L), state.recentlyGiven.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        // Nothing committed during the undo window.
        coVerify(exactly = 0) { markGiven(any(), any()) }
    }

    @Test
    fun `undoMarkGiven reveals the row again without writing`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { observeRecords() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()

            vm.markGiven(record)
            state = awaitItem()
            assertEquals(1L, state.lastMarkedGiven?.id)

            vm.undoMarkGiven()
            state = awaitItem()
            assertNull(state.lastMarkedGiven)
            assertEquals(listOf(1L), state.schedule.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 0) { markGiven(any(), any()) }
    }

    @Test
    fun `onMarkGivenConsumed commits the mark-given write`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { observeRecords() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.markGiven(record)
        vm.onMarkGivenConsumed()
        advanceUntilIdle()

        coVerify(exactly = 1) { markGiven(1L, any()) }
    }

    @Test
    fun `surfaces an error state when the source flow throws`() = runTest {
        every { observeRecords() } returns flow { throw IllegalStateException("boom") }
        val vm = viewModel()

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
        every { observeRecords() } returnsMany listOf(
            flow { throw IllegalStateException("boom") },
            flowOf(emptyList()),
        )
        val vm = viewModel()

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
