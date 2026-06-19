package com.babytracker.ui.vaccine

import app.cash.turbine.test
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.usecase.vaccine.DeleteVaccineRecordUseCase
import com.babytracker.domain.usecase.vaccine.MarkVaccineAdministeredUseCase
import com.babytracker.domain.usecase.vaccine.ObserveVaccineRecordsUseCase
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class VaccineHistoryViewModelTest {
    private val zone = ZoneId.of("UTC")
    private val fixedNow = Instant.ofEpochMilli(100_000)
    private val observe = mockk<ObserveVaccineRecordsUseCase>()
    private val markGiven = mockk<MarkVaccineAdministeredUseCase>()
    private val delete = mockk<DeleteVaccineRecordUseCase>()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = VaccineHistoryViewModel(observe, markGiven, delete, zone) { fixedNow }

    @Test
    fun `splits upcoming ascending and administered by day descending`() = runTest {
        val day16 = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()
        val day15 = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, zone).toInstant()
        every { observe() } returns flowOf(
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
        every { observe() } returns flowOf(emptyList())
        coEvery { markGiven(9, any()) } just Runs
        vm().markGiven(9)
        coVerify { markGiven(9, fixedNow) }
    }

    @Test
    fun `requestDelete optimistically hides the record then commit deletes it`() = runTest {
        val record = VaccineRecord(id = 7, name = "BCG", status = VaccineStatus.ADMINISTERED, administeredDate = fixedNow, createdAt = fixedNow)
        every { observe() } returns flowOf(listOf(record))
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

        viewModel.commitDelete()
        assertNull(viewModel.pendingDelete.value)
        coVerify { delete(7) }
    }

    @Test
    fun `undoDelete reveals the record and does not delete`() = runTest {
        every { observe() } returns flowOf(emptyList())
        val viewModel = vm()
        viewModel.requestDelete(
            VaccineRecord(id = 7, name = "BCG", status = VaccineStatus.ADMINISTERED, administeredDate = fixedNow, createdAt = fixedNow),
        )
        assertEquals(7, viewModel.pendingDelete.value?.id)

        viewModel.undoDelete()
        assertNull(viewModel.pendingDelete.value)
        coVerify(exactly = 0) { delete(any()) }
    }
}
