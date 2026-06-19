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

class AddVaccineRecordUseCaseTest {
    private lateinit var repository: VaccineRepository
    private lateinit var scheduler: VaccineReminderScheduler
    private val fixedNow = Instant.ofEpochMilli(10_000)
    private lateinit var useCase: AddVaccineRecordUseCase

    @BeforeEach
    fun setup() {
        repository = mockk(relaxed = true)
        scheduler = mockk(relaxed = true)
        useCase = AddVaccineRecordUseCase(repository, scheduler) { fixedNow }
    }

    @Test
    fun `schedules future vaccine and arms reminder`() = runTest {
        val captured = slot<VaccineRecord>()
        coEvery { repository.insert(capture(captured)) } returns 5
        val id = useCase("  MMR ", "1st dose", VaccineStatus.SCHEDULED, Instant.ofEpochMilli(50_000), "  ")
        assertEquals(5, id)
        assertEquals("MMR", captured.captured.name)
        assertEquals(VaccineStatus.SCHEDULED, captured.captured.status)
        assertEquals(Instant.ofEpochMilli(50_000), captured.captured.scheduledDate)
        assertEquals(null, captured.captured.notes)
        coVerify { scheduler.schedule(match { it.id == 5L }) }
    }

    @Test
    fun `scheduler failure does not fail the save`() = runTest {
        coEvery { repository.insert(any()) } returns 11
        coEvery { scheduler.schedule(any()) } throws IllegalStateException("alarm boom")
        val id = useCase("MMR", null, VaccineStatus.ADMINISTERED, fixedNow)
        assertEquals(11, id)
    }

    @Test
    fun `administered rejects future date`() = runTest {
        val error = runCatching {
            useCase("MMR", null, VaccineStatus.ADMINISTERED, Instant.ofEpochMilli(20_000))
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `blank name rejected`() = runTest {
        val error = runCatching {
            useCase("   ", null, VaccineStatus.ADMINISTERED, fixedNow)
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }

    @Test
    fun `scheduled rejects non-future date`() = runTest {
        val error = runCatching {
            useCase("MMR", null, VaccineStatus.SCHEDULED, fixedNow) // date == now, not future
        }.exceptionOrNull()
        assertEquals(IllegalArgumentException::class.java, error?.javaClass)
    }
}
