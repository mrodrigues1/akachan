package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class SaveSleepEntryUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: SaveSleepEntryUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SaveSleepEntryUseCase(repository)
    }

    @Test
    fun `invoke inserts record with correct times and type, returns id`() = runTest {
        val start = Instant.parse("2026-04-08T09:30:00Z")
        val end = Instant.parse("2026-04-08T11:00:00Z")
        val slot = slot<SleepRecord>()
        coEvery { repository.insertRecord(capture(slot)) } returns 42L

        val result = useCase(start, end, SleepType.NAP)

        assertEquals(42L, result)
        assertEquals(start, slot.captured.startTime)
        assertEquals(end, slot.captured.endTime)
        assertEquals(SleepType.NAP, slot.captured.sleepType)
    }

    @Test
    fun `invoke works for night sleep spanning midnight`() = runTest {
        val start = Instant.parse("2026-04-07T20:30:00Z")
        val end = Instant.parse("2026-04-08T06:45:00Z")
        val slot = slot<SleepRecord>()
        coEvery { repository.insertRecord(capture(slot)) } returns 7L

        val result = useCase(start, end, SleepType.NIGHT_SLEEP)

        assertEquals(7L, result)
        assertEquals(SleepType.NIGHT_SLEEP, slot.captured.sleepType)
        assertEquals(start, slot.captured.startTime)
        assertEquals(end, slot.captured.endTime)
    }

    @Test
    fun `invoke calls repository insertRecord exactly once`() = runTest {
        coEvery { repository.insertRecord(any()) } returns 1L

        useCase(
            Instant.parse("2026-04-08T09:30:00Z"),
            Instant.parse("2026-04-08T11:00:00Z"),
            SleepType.NAP
        )

        coVerify(exactly = 1) { repository.insertRecord(any()) }
    }
}
