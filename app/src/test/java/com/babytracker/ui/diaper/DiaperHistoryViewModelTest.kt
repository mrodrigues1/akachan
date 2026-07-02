package com.babytracker.ui.diaper

import app.cash.turbine.test
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DiaperHistoryViewModelTest {
    private val zone = ZoneId.of("UTC")
    private val diaperRepository = mockk<DiaperRepository>()
    private val delete = mockk<DeleteDiaperChangeUseCase>()

    @BeforeEach
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun change(id: Long, at: Instant) =
        DiaperChange(id = id, timestamp = at, type = DiaperType.WET, createdAt = at)

    @Test
    fun `groups by day descending`() = runTest {
        val day16 = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()
        val day15 = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, zone).toInstant()
        every { diaperRepository.observeAll() } returns flowOf(listOf(change(2, day16), change(1, day15)))
        val vm = DiaperHistoryViewModel(diaperRepository, delete, zone)
        vm.historyByDateDesc.test {
            // stateIn emits its initial empty value first; skip past it to the mapped value.
            var groups = awaitItem()
            if (groups.isEmpty()) groups = awaitItem()
            assertEquals(2, groups.size)
            assertEquals(LocalDate.of(2026, 6, 16), groups.first().first)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete request then confirm deletes and clears pending`() = runTest {
        every { diaperRepository.observeAll() } returns flowOf(emptyList())
        coEvery { delete(7) } just Runs
        val vm = DiaperHistoryViewModel(diaperRepository, delete, zone)
        val c = change(7, Instant.ofEpochMilli(1_000))

        vm.onDeleteRequest(c)
        assertEquals(7, vm.pendingDelete.value?.id)

        vm.onConfirmDelete()
        assertNull(vm.pendingDelete.value)
        coVerify { delete(7) }
    }

    @Test
    fun `cancel clears pending without deleting`() = runTest {
        every { diaperRepository.observeAll() } returns flowOf(emptyList())
        val vm = DiaperHistoryViewModel(diaperRepository, delete, zone)

        vm.onDeleteRequest(change(7, Instant.ofEpochMilli(1_000)))
        vm.onCancelDelete()

        assertNull(vm.pendingDelete.value)
        coVerify(exactly = 0) { delete(any()) }
    }

    @Test
    fun `confirm delete failure sets deleteError and consume clears it`() = runTest {
        every { diaperRepository.observeAll() } returns flowOf(emptyList())
        coEvery { delete(7) } throws RuntimeException("db write failed")
        val vm = DiaperHistoryViewModel(diaperRepository, delete, zone)

        vm.onDeleteRequest(change(7, Instant.ofEpochMilli(1_000)))
        vm.onConfirmDelete()

        assertTrue(vm.deleteError.value)
        vm.onDeleteErrorConsumed()
        assertFalse(vm.deleteError.value)
    }
}
