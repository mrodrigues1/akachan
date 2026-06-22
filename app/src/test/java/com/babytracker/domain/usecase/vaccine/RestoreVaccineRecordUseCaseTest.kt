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

class RestoreVaccineRecordUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var scheduler: VaccineReminderScheduler
    private lateinit var useCase: RestoreVaccineRecordUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = RestoreVaccineRecordUseCase(repository, scheduler)
    }

    @Test
    fun `re-inserts the record verbatim preserving its id and re-arms the reminder`() = runTest {
        val original = VaccineRecord(
            id = 7,
            name = "BCG",
            status = VaccineStatus.ADMINISTERED,
            administeredDate = Instant.ofEpochMilli(5_000),
            createdAt = Instant.ofEpochMilli(1),
        )
        val captured = slot<VaccineRecord>()
        coEvery { repository.insert(capture(captured)) } returns 7

        useCase(original)

        assertEquals(original, captured.captured)
        coVerify { scheduler.schedule(original) }
    }

    @Test
    fun `restores an overdue scheduled dose without future-date validation`() = runTest {
        val overdue = VaccineRecord(
            id = 3,
            name = "MMR",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(2_000),
            createdAt = Instant.ofEpochMilli(1),
        )
        val captured = slot<VaccineRecord>()
        coEvery { repository.insert(capture(captured)) } returns 3

        useCase(overdue)

        assertEquals(VaccineStatus.SCHEDULED, captured.captured.status)
        assertEquals(Instant.ofEpochMilli(2_000), captured.captured.scheduledDate)
    }

    @Test
    fun `scheduler failure does not fail the restore`() = runTest {
        val original = VaccineRecord(
            id = 1,
            name = "Polio",
            status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(5_000),
            createdAt = Instant.ofEpochMilli(1),
        )
        coEvery { scheduler.schedule(any()) } throws IllegalStateException("alarm boom")

        useCase(original)

        coVerify { repository.insert(original) }
    }
}
