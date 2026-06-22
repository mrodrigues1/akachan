package com.babytracker.domain.usecase.vaccine

import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.VaccineStatus
import com.babytracker.domain.repository.VaccineRepository
import com.babytracker.manager.VaccineReminderScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class UndoMarkVaccineAdministeredUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var scheduler: VaccineReminderScheduler
    private lateinit var useCase: UndoMarkVaccineAdministeredUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = UndoMarkVaccineAdministeredUseCase(repository, scheduler)
    }

    @Test
    fun `writes the original scheduled record back and re-arms the reminder`() = runTest {
        val original = VaccineRecord(
            id = 4,
            name = "BCG",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(5_000),
            createdAt = Instant.ofEpochMilli(1),
        )
        val captured = slot<VaccineRecord>()
        coEvery { repository.update(capture(captured)) } returns Unit

        useCase(original)

        assertEquals(original, captured.captured)
        coVerify { scheduler.schedule(original) }
    }

    @Test
    fun `restores an overdue scheduled date without future-date validation`() = runTest {
        // The deferred validation would reject this past scheduled date; the revert writes directly.
        val overdue = VaccineRecord(
            id = 8,
            name = "MMR",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(2_000),
            createdAt = Instant.ofEpochMilli(1),
        )
        val captured = slot<VaccineRecord>()
        coEvery { repository.update(capture(captured)) } returns Unit

        useCase(overdue)

        assertEquals(VaccineStatus.SCHEDULED, captured.captured.status)
        assertEquals(Instant.ofEpochMilli(2_000), captured.captured.scheduledDate)
    }

    @Test
    fun `scheduler failure does not fail the revert`() = runTest {
        val original = VaccineRecord(
            id = 1,
            name = "Polio",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(5_000),
            createdAt = Instant.ofEpochMilli(1),
        )
        coEvery { scheduler.schedule(any()) } throws IllegalStateException("alarm boom")

        useCase(original)

        coVerify { repository.update(original) }
    }
}
