package com.babytracker.ui.diaper

import app.cash.turbine.test
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import com.babytracker.domain.usecase.diaper.DeleteDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.LogDiaperChangeUseCase
import com.babytracker.domain.usecase.diaper.ObserveDiaperChangesUseCase
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DiaperHistoryViewModelTest {
    private val zone = ZoneId.of("UTC")
    private val observe = mockk<ObserveDiaperChangesUseCase>()
    private val delete = mockk<DeleteDiaperChangeUseCase>()
    private val log = mockk<LogDiaperChangeUseCase>(relaxed = true)

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
        every { observe() } returns flowOf(listOf(change(2, day16), change(1, day15)))
        val vm = DiaperHistoryViewModel(observe, delete, log, zone)
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
    fun `delete delegates and emits, undo re-logs`() = runTest {
        every { observe() } returns flowOf(emptyList())
        coEvery { delete(7) } just Runs
        val vm = DiaperHistoryViewModel(observe, delete, log, zone)
        val c = change(7, Instant.ofEpochMilli(1_000))

        vm.deletions.test {
            vm.onDelete(c)
            assertEquals(7, awaitItem().id)
            cancelAndIgnoreRemainingEvents()
        }
        coVerify { delete(7) }

        vm.onUndoDelete(c)
        coVerify { log(c.type, c.timestamp, c.notes) }
    }
}
