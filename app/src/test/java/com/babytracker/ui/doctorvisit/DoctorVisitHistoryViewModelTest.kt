package com.babytracker.ui.doctorvisit

import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.repository.DoctorVisitRepository
import com.babytracker.domain.usecase.doctorvisit.DeleteDoctorVisitUseCase
import com.babytracker.manager.DoctorVisitReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class DoctorVisitHistoryViewModelTest {
    private val repository = mockk<DoctorVisitRepository>(relaxed = true)
    private val deleteVisit = mockk<DeleteDoctorVisitUseCase>(relaxed = true)
    private val scheduler = mockk<DoctorVisitReminderScheduler>(relaxed = true)
    private val now = Instant.ofEpochMilli(1_000)

    @BeforeEach
    fun setup() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterEach
    fun tearDown() = Dispatchers.resetMain()

    private fun vm() = DoctorVisitHistoryViewModel(
        repository, deleteVisit, scheduler,
    ) { now }

    private fun visit(id: Long, dateMs: Long) =
        DoctorVisit(id = id, date = Instant.ofEpochMilli(dateMs), createdAt = Instant.EPOCH)

    @Test
    fun `partitions and orders upcoming asc and past desc with counts`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(
            listOf(
                visit(1, 500), // past
                visit(2, 800), // past (more recent)
                visit(3, 3_000), // upcoming
                visit(4, 2_000), // upcoming (sooner)
            ),
        )
        every { repository.observeAttachedQuestionCounts() } returns flowOf(mapOf(4L to 2))
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        advanceUntilIdle()
        val s = vm.uiState.value
        assertEquals(listOf(4L, 3L), s.upcoming.map { it.id }) // asc by date
        assertEquals(listOf(2L, 1L), s.past.map { it.id }) // desc by date
        assertEquals(2, s.questionCounts[4L])
    }

    @Test
    fun `delete records lastDeleted`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeAttachedQuestionCounts() } returns flowOf(emptyMap())
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onDelete(visit(6, 9_000))
        advanceUntilIdle()
        coVerify { deleteVisit(6) }
        assertEquals(6L, vm.uiState.value.lastDeleted?.id)
    }

    @Test
    fun `undo re-inserts captured visit with new id and re-arms reminder`() = runTest {
        every { repository.observeAllVisits() } returns flowOf(emptyList())
        every { repository.observeAttachedQuestionCounts() } returns flowOf(emptyMap())
        coEvery { repository.insertVisit(any()) } returns 99
        val vm = vm()
        backgroundScope.launch { vm.uiState.collect {} }
        val deleted = visit(6, 9_000).copy(providerName = "Dr A", notes = "n")
        vm.onDelete(deleted)
        advanceUntilIdle()
        vm.onUndoDelete()
        advanceUntilIdle()
        val captured = slot<DoctorVisit>()
        coVerify { repository.insertVisit(capture(captured)) }
        assertEquals(0L, captured.captured.id) // 0 → Room autogenerates
        assertEquals("Dr A", captured.captured.providerName)
        coVerify { scheduler.schedule(match { it.id == 99L }) }
        assertNull(vm.uiState.value.lastDeleted)
    }
}
