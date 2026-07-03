package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import io.mockk.coEvery
import io.mockk.coJustRun
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class StopSleepRecordUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: StopSleepRecordUseCase

    private val inProgressRecord = SleepRecord(
        id = 10L,
        startTime = Instant.now().minusSeconds(600),
        sleepType = SleepType.NAP,
        notes = "test note"
    )

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = StopSleepRecordUseCase(repository)
    }

    @Test
    fun invokeMatchingInProgressUpdatesWithNonNullEndTime() = runTest {
        val slot = slot<SleepRecord>()
        coEvery { repository.getActiveRecord() } returns inProgressRecord
        coJustRun { repository.updateRecord(capture(slot)) }

        useCase(10L)

        assertNotNull(slot.captured.endTime)
    }

    @Test
    fun invokeMatchingInProgressPreservesOtherFields() = runTest {
        val slot = slot<SleepRecord>()
        coEvery { repository.getActiveRecord() } returns inProgressRecord
        coJustRun { repository.updateRecord(capture(slot)) }

        useCase(10L)

        assertEquals(10L, slot.captured.id)
        assertEquals(SleepType.NAP, slot.captured.sleepType)
        assertEquals(inProgressRecord.startTime, slot.captured.startTime)
        assertEquals("test note", slot.captured.notes)
    }

    @Test
    fun invokeWrongIdDoesNotCallUpdate() = runTest {
        coEvery { repository.getActiveRecord() } returns inProgressRecord

        useCase(999L)

        coVerify(exactly = 0) { repository.updateRecord(any()) }
    }

    @Test
    fun invokeRecordAlreadyStoppedDoesNotCallUpdate() = runTest {
        // A stopped record is no longer the active record.
        coEvery { repository.getActiveRecord() } returns null

        useCase(10L)

        coVerify(exactly = 0) { repository.updateRecord(any()) }
    }

    @Test
    fun invokeNoActiveRecordDoesNotCallUpdate() = runTest {
        coEvery { repository.getActiveRecord() } returns null

        useCase(10L)

        coVerify(exactly = 0) { repository.updateRecord(any()) }
    }

    @Test
    fun invokeActiveRecordWithDifferentIdDoesNotCallUpdate() = runTest {
        val other = SleepRecord(id = 20L, startTime = Instant.now(), sleepType = SleepType.NIGHT_SLEEP)
        coEvery { repository.getActiveRecord() } returns other

        useCase(10L)

        coVerify(exactly = 0) { repository.updateRecord(any()) }
    }
}
