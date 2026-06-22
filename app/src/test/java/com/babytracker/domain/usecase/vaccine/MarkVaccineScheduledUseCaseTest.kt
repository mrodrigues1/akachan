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

class MarkVaccineScheduledUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var reminderScheduler: VaccineReminderScheduler
    private lateinit var useCase: MarkVaccineScheduledUseCase

    private val target = Instant.parse("2026-07-01T00:00:00Z")
    private val toSchedule = VaccineRecord(
        id = 5, name = "MMR", status = VaccineStatus.TO_SCHEDULE,
        scheduledDate = target, createdAt = Instant.parse("2026-06-01T00:00:00Z"),
    )

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        reminderScheduler = mockk(relaxed = true)
        useCase = MarkVaccineScheduledUseCase(repository, reminderScheduler)
    }

    @Test
    fun `flips to-schedule to scheduled keeping the date and re-arms`() = runTest {
        coEvery { repository.getById(5) } returns toSchedule
        val slot = slot<VaccineRecord>()
        coEvery { repository.update(capture(slot)) } returns Unit

        useCase(5)

        assertEquals(VaccineStatus.SCHEDULED, slot.captured.status)
        assertEquals(target, slot.captured.scheduledDate)
        coVerify { reminderScheduler.schedule(match { it.status == VaccineStatus.SCHEDULED }) }
    }

    @Test
    fun `no-op for a non-to-schedule record`() = runTest {
        coEvery { repository.getById(5) } returns toSchedule.copy(status = VaccineStatus.SCHEDULED)
        useCase(5)
        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `no-op for a missing record`() = runTest {
        coEvery { repository.getById(5) } returns null
        useCase(5)
        coVerify(exactly = 0) { repository.update(any()) }
    }
}
