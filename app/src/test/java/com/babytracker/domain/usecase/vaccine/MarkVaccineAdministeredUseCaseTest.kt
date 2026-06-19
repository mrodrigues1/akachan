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

class MarkVaccineAdministeredUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var scheduler: VaccineReminderScheduler
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: MarkVaccineAdministeredUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = MarkVaccineAdministeredUseCase(repository, scheduler) { fixedNow }
    }

    @Test
    fun `flips to administered retaining scheduled date and cancels reminder`() = runTest {
        coEvery { repository.getById(3) } returns VaccineRecord(
            id = 3, name = "BCG", status = VaccineStatus.SCHEDULED,
            scheduledDate = Instant.ofEpochMilli(5_000), createdAt = Instant.ofEpochMilli(1),
        )
        val captured = slot<VaccineRecord>()
        coEvery { repository.update(capture(captured)) } returns Unit
        useCase(3, Instant.ofEpochMilli(8_000))
        assertEquals(VaccineStatus.ADMINISTERED, captured.captured.status)
        assertEquals(Instant.ofEpochMilli(8_000), captured.captured.administeredDate)
        assertEquals(Instant.ofEpochMilli(5_000), captured.captured.scheduledDate)
        coVerify { scheduler.cancel(3) }
    }

    @Test
    fun `missing id is a no-op`() = runTest {
        coEvery { repository.getById(99) } returns null
        useCase(99)
        coVerify(exactly = 0) { repository.update(any()) }
    }

    @Test
    fun `already administered record is not rewritten but reminder still cancelled`() = runTest {
        coEvery { repository.getById(7) } returns VaccineRecord(
            id = 7, name = "MMR", status = VaccineStatus.ADMINISTERED,
            administeredDate = Instant.ofEpochMilli(2_000), createdAt = Instant.ofEpochMilli(1),
        )
        useCase(7, Instant.ofEpochMilli(9_000))
        coVerify(exactly = 0) { repository.update(any()) }
        // Stale-alarm cleanup runs even when the status is unchanged.
        coVerify { scheduler.cancel(7) }
    }

    @Test
    fun `future administered date rejected`() = runTest {
        val error = runCatching {
            useCase(3, Instant.ofEpochMilli(20_000))
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
