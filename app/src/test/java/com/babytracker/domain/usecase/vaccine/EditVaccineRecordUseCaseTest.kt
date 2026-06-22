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

class EditVaccineRecordUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var scheduler: VaccineReminderScheduler
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: EditVaccineRecordUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = EditVaccineRecordUseCase(repository, scheduler)
    }

    @Test
    fun `valid edit normalizes optional fields and re-arms reminder`() = runTest {
        val captured = slot<VaccineRecord>()
        coEvery { repository.update(capture(captured)) } returns Unit
        useCase(
            VaccineRecord(
                id = 2,
                name = "  MMR ",
                doseLabel = "  ",
                status = VaccineStatus.SCHEDULED,
                scheduledDate = Instant.ofEpochMilli(50_000),
                notes = "  ",
                createdAt = fixedNow,
            ),
        )
        assertEquals("MMR", captured.captured.name)
        assertEquals(null, captured.captured.doseLabel)
        assertEquals(null, captured.captured.notes)
        coVerify { scheduler.schedule(match { it.id == 2L }) }
    }

    @Test
    fun `administered allows a future date`() = runTest {
        // Future "given" dates are permitted now (the UI warns instead of blocking the save).
        val captured = slot<VaccineRecord>()
        val future = Instant.ofEpochMilli(50_000) // after fixedNow (10_000)
        coEvery { repository.update(capture(captured)) } returns Unit
        useCase(
            VaccineRecord(
                id = 3,
                name = "MMR",
                status = VaccineStatus.ADMINISTERED,
                administeredDate = future,
                createdAt = fixedNow,
            ),
        )
        assertEquals(future, captured.captured.administeredDate)
        coVerify { scheduler.schedule(match { it.id == 3L }) }
    }

    @Test
    fun `administered without date rejected`() = runTest {
        val error = runCatching {
            useCase(
                VaccineRecord(
                    id = 1,
                    name = "MMR",
                    status = VaccineStatus.ADMINISTERED,
                    administeredDate = null,
                    createdAt = fixedNow,
                ),
            )
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `scheduled without date rejected`() = runTest {
        val error = runCatching {
            useCase(
                VaccineRecord(
                    id = 1,
                    name = "MMR",
                    status = VaccineStatus.SCHEDULED,
                    scheduledDate = null,
                    createdAt = fixedNow,
                ),
            )
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `to-schedule edit persists and re-arms reminder`() = runTest {
        val captured = slot<VaccineRecord>()
        coEvery { repository.update(capture(captured)) } returns Unit
        useCase(
            VaccineRecord(
                id = 4,
                name = "  MMR ",
                status = VaccineStatus.TO_SCHEDULE,
                scheduledDate = Instant.ofEpochMilli(50_000),
                createdAt = fixedNow,
            ),
        )
        assertEquals("MMR", captured.captured.name)
        assertEquals(VaccineStatus.TO_SCHEDULE, captured.captured.status)
        coVerify { scheduler.schedule(match { it.id == 4L }) }
    }

    @Test
    fun `to-schedule without date rejected`() = runTest {
        val error = runCatching {
            useCase(
                VaccineRecord(
                    id = 1,
                    name = "MMR",
                    status = VaccineStatus.TO_SCHEDULE,
                    scheduledDate = null,
                    createdAt = fixedNow,
                ),
            )
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `blank name rejected`() = runTest {
        val error = runCatching {
            useCase(
                VaccineRecord(
                    id = 1,
                    name = "   ",
                    status = VaccineStatus.ADMINISTERED,
                    administeredDate = fixedNow,
                    createdAt = fixedNow,
                ),
            )
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
