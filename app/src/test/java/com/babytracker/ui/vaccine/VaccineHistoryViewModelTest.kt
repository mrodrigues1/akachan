package com.babytracker.ui.vaccine

import app.cash.turbine.test
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.domain.usecase.vaccine.DeleteVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineScheduledUseCase
import com.babytracker.domain.usecase.vaccine.RestoreVaccineRecordUseCase
import com.babytracker.testutil.MainDispatcherExtension
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.extension.RegisterExtension

class VaccineHistoryViewModelTest {
    private val zone = ZoneId.of("UTC")
    private val fixedNow = Instant.ofEpochMilli(100_000)
    private val vaccineRepository = mockk<VaccineRepository>()
    private val markGiven = mockk<MarkVaccineAdministeredUseCase>()
    private val markScheduled = mockk<MarkVaccineScheduledUseCase>(relaxed = true)
    private val delete = mockk<DeleteVaccineRecordUseCase>()
    private val restore = mockk<RestoreVaccineRecordUseCase>(relaxed = true)

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    private fun vm() = VaccineHistoryViewModel(vaccineRepository, markGiven, markScheduled, delete, restore, zone) { fixedNow }

    @Test
    fun `splits upcoming ascending and administered by day descending`() = runTest {
        val day16 = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()
        val day15 = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, zone).toInstant()
        every { vaccineRepository.observeAll() } returns flowOf(
            listOf(
                VaccineRecord(id = 1, name = "Future", status = VaccineStatus.SCHEDULED, scheduledDate = Instant.ofEpochMilli(200_000), createdAt = fixedNow),
                VaccineRecord(id = 2, name = "Overdue", status = VaccineStatus.SCHEDULED, scheduledDate = Instant.ofEpochMilli(5_000), createdAt = fixedNow),
                VaccineRecord(id = 3, name = "Given16", status = VaccineStatus.ADMINISTERED, administeredDate = day16, createdAt = day16),
                VaccineRecord(id = 4, name = "Given15", status = VaccineStatus.ADMINISTERED, administeredDate = day15, createdAt = day15),
            ),
        )
        vm().uiState.test {
            var state = awaitItem()
            if (state.isEmpty) state = awaitItem()
            assertEquals(listOf("Overdue", "Future"), state.upcoming.map { it.name })
            assertEquals(LocalDate.of(2026, 6, 16), state.administeredByDate.first().first)
            assertEquals(2, state.administeredByDate.size)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markGiven invokes the use case`() = runTest {
        every { vaccineRepository.observeAll() } returns flowOf(emptyList())
        coEvery { markGiven(9, any()) } just Runs
        vm().markGiven(9)
        coVerify { markGiven(9, fixedNow) }
    }

    @Test
    fun `to-schedule records populate the toSchedule list sorted by target date`() = runTest {
        val later = VaccineRecord(id = 1, name = "B", status = VaccineStatus.TO_SCHEDULE, scheduledDate = Instant.ofEpochMilli(300_000), createdAt = fixedNow)
        val sooner = VaccineRecord(id = 2, name = "A", status = VaccineStatus.TO_SCHEDULE, scheduledDate = Instant.ofEpochMilli(200_000), createdAt = fixedNow)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(later, sooner))
        vm().uiState.test {
            var state = awaitItem()
            if (state.isEmpty) state = awaitItem()
            assertEquals(listOf(2L, 1L), state.toSchedule.map { it.id })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `markScheduled delegates to the use case`() = runTest {
        every { vaccineRepository.observeAll() } returns flowOf(emptyList())
        vm().markScheduled(5)
        coVerify { markScheduled(5) }
    }

    @Test
    fun `requestDelete commits immediately and optimistically hides the record`() = runTest {
        val record = VaccineRecord(id = 7, name = "BCG", status = VaccineStatus.ADMINISTERED, administeredDate = fixedNow, createdAt = fixedNow)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        coEvery { delete(7) } just Runs
        val viewModel = vm()

        viewModel.uiState.test {
            var s = awaitItem()
            if (s.isEmpty) s = awaitItem()
            assertEquals(1, s.administeredByDate.sumOf { it.second.size })
            viewModel.requestDelete(record)
            val hidden = awaitItem()
            assertEquals(0, hidden.administeredByDate.sumOf { it.second.size })
            assertEquals(7, viewModel.pendingDelete.value?.id)
            cancelAndIgnoreRemainingEvents()
        }

        // Committed at request time so it survives the screen closing before the snackbar resolves.
        coVerify { delete(7) }
        viewModel.commitDelete()
        assertNull(viewModel.pendingDelete.value)
    }

    @Test
    fun `undoDelete restores the record after the immediate delete`() = runTest {
        val record = VaccineRecord(id = 7, name = "BCG", status = VaccineStatus.ADMINISTERED, administeredDate = fixedNow, createdAt = fixedNow)
        every { vaccineRepository.observeAll() } returns flowOf(emptyList())
        coEvery { delete(7) } just Runs
        val viewModel = vm()
        viewModel.requestDelete(record)
        assertEquals(7, viewModel.pendingDelete.value?.id)

        viewModel.undoDelete()
        assertNull(viewModel.pendingDelete.value)
        coVerify { delete(7) }
        coVerify { restore(record) }
    }

    @Test
    fun `markGiven failure sets writeError`() = runTest {
        every { vaccineRepository.observeAll() } returns flowOf(emptyList())
        coEvery { markGiven(9, any()) } throws RuntimeException("db write failed")
        val viewModel = vm()

        viewModel.uiState.test {
            var s = awaitItem()
            viewModel.markGiven(9)
            while (!s.writeError) s = awaitItem()

            viewModel.onWriteErrorConsumed()
            while (s.writeError) s = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `requestDelete failure unhides the record and sets writeError`() = runTest {
        val record = VaccineRecord(id = 7, name = "BCG", status = VaccineStatus.ADMINISTERED, administeredDate = fixedNow, createdAt = fixedNow)
        every { vaccineRepository.observeAll() } returns flowOf(listOf(record))
        coEvery { delete(7) } throws RuntimeException("db write failed")
        val viewModel = vm()

        viewModel.uiState.test {
            var s = awaitItem()
            if (s.isEmpty) s = awaitItem()
            viewModel.requestDelete(record)
            while (!s.writeError) s = awaitItem()

            // Optimistic hide reverted: the record is still in the DB and visible again.
            assertEquals(1, s.administeredByDate.sumOf { it.second.size })
            assertNull(viewModel.pendingDelete.value)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
