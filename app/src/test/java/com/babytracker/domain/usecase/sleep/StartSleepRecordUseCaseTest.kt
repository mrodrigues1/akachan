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
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class StartSleepRecordUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: StartSleepRecordUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = StartSleepRecordUseCase(repository)
    }

    @Test
    fun invokeWithNapCreatesRecordWithNapType() = runTest {
        val slot = slot<SleepRecord>()
        coEvery { repository.insertRecord(capture(slot)) } returns 1L

        useCase(SleepType.NAP)

        assertEquals(SleepType.NAP, slot.captured.sleepType)
    }

    @Test
    fun invokeWithNightSleepCreatesRecordWithNightSleepType() = runTest {
        val slot = slot<SleepRecord>()
        coEvery { repository.insertRecord(capture(slot)) } returns 1L

        useCase(SleepType.NIGHT_SLEEP)

        assertEquals(SleepType.NIGHT_SLEEP, slot.captured.sleepType)
    }

    @Test
    fun invokeReturnedRecordHasIdFromRepository() = runTest {
        coEvery { repository.insertRecord(any()) } returns 42L

        val result = useCase(SleepType.NAP)

        assertEquals(42L, result.id)
    }

    @Test
    fun invokeReturnedRecordHasNonNullStartTime() = runTest {
        coEvery { repository.insertRecord(any()) } returns 1L

        val result = useCase(SleepType.NAP)

        assertNotNull(result.startTime)
    }

    @Test
    fun invokeReturnedRecordHasNullEndTime() = runTest {
        coEvery { repository.insertRecord(any()) } returns 1L

        val result = useCase(SleepType.NAP)

        assertNull(result.endTime)
    }

    @Test
    fun invokeCallsRepositoryInsertRecordExactlyOnce() = runTest {
        coEvery { repository.insertRecord(any()) } returns 1L

        useCase(SleepType.NAP)

        coVerify(exactly = 1) { repository.insertRecord(any()) }
    }
}
