package com.babytracker.ui.vaccine

import app.cash.turbine.test
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.domain.usecase.vaccine.DeleteVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineScheduledUseCase
import com.babytracker.domain.usecase.vaccine.RestoreVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.UndoMarkVaccineAdministeredUseCase
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
import java.time.ZoneId
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
class VaccineDashboardViewModelTest {
    private val vaccineRepository = mockk<VaccineRepository>()
    private val markGiven = mockk<MarkVaccineAdministeredUseCase>(relaxed = true)
    private val markScheduled = mockk<MarkVaccineScheduledUseCase>(relaxed = true)
    private val undoMarkGiven = mockk<UndoMarkVaccineAdministeredUseCase>(relaxed = true)
    private val delete = mockk<DeleteVaccineRecordUseCase>(relaxed = true)
    private val restore = mockk<RestoreVaccineRecordUseCase>(relaxed = true)

    private val zone = ZoneId.of("UTC")
    private val nowInstant = Instant.parse("2026-06-21T12:00:00Z")
    private val now: () -> Instant = { nowInstant }

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

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

    private fun toSchedule(id: Long, offsetDays: Long) = VaccineRecord(
        id = id,
        name = "Vaccine $id",
        status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = nowInstant.plusSeconds(offsetDays * 86_400),
        createdAt = Instant.EPOCH,
    )

    private fun viewModel() =
        VaccineDashboardViewModel(vaccineRepository, markGiven, markScheduled, undoMarkGiven, delete, restore, zone, now)

    @Test
    fun `derives overdue hero, schedule order, and recently given`() = runTest {
        every { vaccineRepository.observeAll() } returns flowOf(
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
    fun `groups every upcoming vaccine on the soonest day into nextVaccines`() = runTest {
        every { vaccineRepository.observeAll() } returns flowOf(
            listOf(
                scheduled(1, offsetDays = 7),
                scheduled(2, offsetDays = 7),
                scheduled(3, offsetDays = 14),
                scheduled(4, offsetDays = -2),
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()

            // Both day-+7 doses grouped (date then name order); the day-+14 and overdue doses excluded.
            assertEquals(listOf(1L, 2L), state.nextVaccines.map { it.id })
            assertEquals(1L, state.nextVaccine?.id)
            assertEquals(7, state.nextInDays)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `nextVaccines holds a single record when the soonest day has one dose`() = runTest {
        every { vaccineRepository.observeAll() } returns flowOf(
            listOf(
                scheduled(1, offsetDays = 3),
                scheduled(2, offsetDays = 10),
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()

            assertEquals(listOf(1L), state.nextVaccines.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `dose due today is next up, never overdue`() = runTest {
        // Scheduled earlier the same calendar day: the screenshot's "Overdue by 0 days" Hib case.
        val earlierToday = VaccineRecord(
            id = 1,
            name = "Hib",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = nowInstant.minusSeconds(3_600),
            createdAt = Instant.EPOCH,
        )
        every { vaccineRepository.observeAll() } returns flowOf(listOf(earlierToday))
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()

            assertNull(state.mostOverdue)
            assertEquals(0, state.overdueCount)
            assertEquals(listOf(1L), state.nextVaccines.map { it.id })
            assertEquals(0, state.nextInDays)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `first run flag set when nothing recorded`() = runTest {
        every { vaccineRepository.observeAll() } returns flowOf(emptyList())
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
    fun `markGiven commits immediately, hides the row, and shows it as given`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
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

        // The write fires the moment the parent taps, so it can't be lost when the screen leaves
        // composition before the undo snackbar resolves (the original bug).
        coVerify(exactly = 1) { markGiven(1L, any()) }
    }

    @Test
    fun `undoMarkGiven reverts the committed write and reveals the row`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
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

        coVerify(exactly = 1) { markGiven(1L, any()) }
        // Undo writes the original scheduled record straight back.
        coVerify(exactly = 1) { undoMarkGiven(record) }
    }

    @Test
    fun `onMarkGivenConsumed closes the undo window without a second write`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.markGiven(record)
        vm.onMarkGivenConsumed()
        advanceUntilIdle()

        // Committed once at mark time; consuming the snackbar must not write again or revert.
        coVerify(exactly = 1) { markGiven(1L, any()) }
        coVerify(exactly = 0) { undoMarkGiven(any()) }
    }

    @Test
    fun `marking the second of two same-day doses commits both`() = runTest {
        // Regression: previously only the first committed (via flush on the next mark) while the
        // second stayed pending and was lost if the screen closed before the snackbar resolved.
        val first = scheduled(1, offsetDays = 7)
        val second = scheduled(2, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(first, second))
        val vm = viewModel()

        vm.markGiven(first)
        vm.markGiven(second)
        advanceUntilIdle()

        coVerify(exactly = 1) { markGiven(1L, any()) }
        coVerify(exactly = 1) { markGiven(2L, any()) }
    }

    @Test
    fun `requestDelete commits immediately and optimistically hides the row`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertEquals(listOf(1L), state.schedule.map { it.id })

            vm.requestDelete(record)
            state = awaitItem()
            assertEquals(1L, state.lastDeleted?.id)
            assertTrue(state.schedule.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        // The delete fires immediately so it can't be lost if the screen leaves composition.
        coVerify(exactly = 1) { delete(1L) }
    }

    @Test
    fun `onDeleteConsumed closes the undo window without a second delete`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.requestDelete(record)
        vm.onDeleteConsumed()
        advanceUntilIdle()

        coVerify(exactly = 1) { delete(1L) }
        coVerify(exactly = 0) { restore(any()) }
    }

    @Test
    fun `undoDelete restores the deleted record`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.requestDelete(record)
        vm.undoDelete()
        advanceUntilIdle()

        coVerify(exactly = 1) { delete(1L) }
        coVerify(exactly = 1) { restore(record) }
    }

    @Test
    fun `to-schedule doses populate the toSchedule section and never the hero`() = runTest {
        every { vaccineRepository.observeAll() } returns flowOf(
            listOf(
                toSchedule(1, offsetDays = 20),
                toSchedule(2, offsetDays = 5),
            ),
        )
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            // Sorted by target date ascending.
            assertEquals(listOf(2L, 1L), state.toSchedule.map { it.id })
            assertNull(state.nextVaccine)
            assertNull(state.mostOverdue)
            assertTrue(state.schedule.isEmpty())
            assertTrue(!state.isFirstRun)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markScheduled commits immediately and opens the undo window`() = runTest {
        val record = toSchedule(1, offsetDays = 20)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            assertEquals(listOf(1L), state.toSchedule.map { it.id })

            vm.markScheduled(record)
            state = awaitItem()
            assertEquals(1L, state.lastMarkedScheduled?.id)
            assertTrue(state.toSchedule.isEmpty())
            cancelAndIgnoreRemainingEvents()
        }

        coVerify(exactly = 1) { markScheduled(1L) }
    }

    @Test
    fun `undoMarkScheduled writes the original record back`() = runTest {
        val record = toSchedule(1, offsetDays = 20)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        val vm = viewModel()

        vm.markScheduled(record)
        vm.undoMarkScheduled()
        advanceUntilIdle()

        coVerify(exactly = 1) { markScheduled(1L) }
        coVerify(exactly = 1) { undoMarkGiven(record) }
    }

    @Test
    fun `surfaces an error state when the source flow throws`() = runTest {
        every { vaccineRepository.observeAll() } returns flow { throw IllegalStateException("boom") }
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
        every { vaccineRepository.observeAll() } returnsMany listOf(
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

    @Test
    fun `markGiven failure reverts the optimistic mark and sets writeError`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        coEvery { markGiven(any(), any()) } throws RuntimeException("db write failed")
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            vm.markGiven(record)
            while (!state.writeError) state = awaitItem()

            // Optimistic mark reverted: the row is back in the schedule, not in recently given.
            assertNull(state.lastMarkedGiven)
            assertEquals(listOf(1L), state.schedule.map { it.id })
            assertEquals(0, state.givenCount)

            vm.onWriteErrorConsumed()
            while (state.writeError) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requestDelete failure unhides the row and sets writeError`() = runTest {
        val record = scheduled(1, offsetDays = 7)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        coEvery { delete(1) } throws RuntimeException("db write failed")
        val vm = viewModel()

        vm.uiState.test {
            var state = awaitItem()
            if (state.isLoading) state = awaitItem()
            vm.requestDelete(record)
            while (!state.writeError) state = awaitItem()

            // Optimistic hide reverted: the record is still in the DB and visible again.
            assertNull(state.lastDeleted)
            assertEquals(listOf(1L), state.schedule.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }
}
