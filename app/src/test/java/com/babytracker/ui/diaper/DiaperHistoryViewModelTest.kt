package com.babytracker.ui.diaper

import app.cash.turbine.test
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.repository.DiaperRepository
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.jupiter.api.extension.RegisterExtension

class DiaperHistoryViewModelTest {
    private val zone = ZoneId.of("UTC")
    private val diaperRepository = mockk<DiaperRepository>()
    private val delete = mockk<DeleteDiaperChangeUseCase>()

    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    private fun change(id: Long, at: Instant) =
        DiaperChange(id = id, timestamp = at, type = DiaperType.WET, createdAt = at)

    @Test
    fun `groups by day descending`() = runTest {
        val day16 = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()
        val day15 = ZonedDateTime.of(2026, 6, 15, 9, 0, 0, 0, zone).toInstant()
        every { diaperRepository.observeRecent(any()) } returns flowOf(listOf(change(2, day16), change(1, day15)))
        val vm = DiaperHistoryViewModel(diaperRepository, delete, zone)
        vm.historyByDateDesc.test {
            // stateIn emits its initial empty value first; skip past it to the mapped value.
            var window = awaitItem()
            if (window.days.isEmpty()) window = awaitItem()
            assertEquals(2, window.days.size)
            assertEquals(LocalDate.of(2026, 6, 16), window.days.first().first)
            assertFalse(window.hasMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history window flags hasMore and grows on load more`() = runTest {
        val base = ZonedDateTime.of(2026, 6, 16, 8, 0, 0, 0, zone).toInstant()
        // 51 rows: one past the first page of 50, so hasMore is derived without a count query.
        val changes = List(51) { i -> change(i + 1L, base.minusSeconds(3_600L * i)) }
        every { diaperRepository.observeRecent(51) } returns flowOf(changes)
        every { diaperRepository.observeRecent(101) } returns flowOf(changes)
        val vm = DiaperHistoryViewModel(diaperRepository, delete, zone)

        vm.historyByDateDesc.test {
            var window = awaitItem()
            while (window.changeCount < 50) {
                window = awaitItem()
            }
            assertTrue(window.hasMore)

            // Second call arrives before the grown window emits: it must be ignored, otherwise the
            // limit reaches 150 and the unstubbed observeRecent(151) fails this test.
            vm.onLoadMoreHistory()
            vm.onLoadMoreHistory()

            while (window.changeCount != 51) {
                window = awaitItem()
            }
            assertFalse(window.hasMore)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `delete request then confirm deletes and clears pending`() = runTest {
        every { diaperRepository.observeRecent(any()) } returns flowOf(emptyList())
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
        every { diaperRepository.observeRecent(any()) } returns flowOf(emptyList())
        val vm = DiaperHistoryViewModel(diaperRepository, delete, zone)

        vm.onDeleteRequest(change(7, Instant.ofEpochMilli(1_000)))
        vm.onCancelDelete()

        assertNull(vm.pendingDelete.value)
        coVerify(exactly = 0) { delete(any()) }
    }

    @Test
    fun `confirm delete failure sets deleteError and consume clears it`() = runTest {
        every { diaperRepository.observeRecent(any()) } returns flowOf(emptyList())
        coEvery { delete(7) } throws RuntimeException("db write failed")
        val vm = DiaperHistoryViewModel(diaperRepository, delete, zone)

        vm.onDeleteRequest(change(7, Instant.ofEpochMilli(1_000)))
        vm.onConfirmDelete()

        assertTrue(vm.deleteError.value)
        vm.onDeleteErrorConsumed()
        assertFalse(vm.deleteError.value)
    }
}
