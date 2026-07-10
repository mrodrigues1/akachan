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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant

class UpdateSleepEntryUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: UpdateSleepEntryUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = UpdateSleepEntryUseCase(repository, Clock.systemDefaultZone())
        coEvery { repository.inTransaction<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
        coEvery { repository.getActiveRecord() } returns null
        coEvery { repository.getCompletedRecordsBetween(any(), any()) } returns emptyList()
    }

    @Test
    fun `invoke preserves provided timezone provenance`() = runTest {
        val start = Instant.parse("2026-04-08T09:30:00Z")
        val end = Instant.parse("2026-04-08T11:00:00Z")
        val slot = slot<SleepRecord>()
        coJustRun { repository.updateRecord(capture(slot)) }

        useCase(
            id = 7L,
            startTime = start,
            endTime = end,
            type = SleepType.NAP,
            timezoneId = "America/New_York",
        )

        coVerify(exactly = 1) { repository.updateRecord(any()) }
        assertEquals("America/New_York", slot.captured.timezoneId)
    }

    @Test
    fun `invoke throws and skips update when endTime is not after startTime`() = runTest {
        val start = Instant.parse("2026-04-08T09:30:00Z")

        val thrown = runCatching {
            useCase(id = 1L, startTime = start, endTime = start, type = SleepType.NAP)
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        coVerify(exactly = 0) { repository.updateRecord(any()) }
    }

    @Test
    fun `invoke does not flag overlap against the record being edited`() = runTest {
        val start = Instant.parse("2026-04-08T21:00:00Z")
        val end = Instant.parse("2026-04-09T05:00:00Z")
        val self = SleepRecord(
            id = 9L,
            startTime = start,
            endTime = end,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        coEvery { repository.getCompletedRecordsBetween(start, end) } returns listOf(self)
        coJustRun { repository.updateRecord(any()) }

        useCase(id = 9L, startTime = start, endTime = end, type = SleepType.NIGHT_SLEEP)

        coVerify(exactly = 1) { repository.updateRecord(any()) }
    }

    @Test
    fun `invoke throws when the edited night sleep overlaps a different existing record`() = runTest {
        val start = Instant.parse("2026-04-08T21:00:00Z")
        val end = Instant.parse("2026-04-09T05:00:00Z")
        val other = SleepRecord(
            id = 2L,
            startTime = Instant.parse("2026-04-08T20:00:00Z"),
            endTime = Instant.parse("2026-04-09T02:00:00Z"),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        coEvery { repository.getCompletedRecordsBetween(start, end) } returns listOf(other)

        val thrown = runCatching {
            useCase(id = 9L, startTime = start, endTime = end, type = SleepType.NIGHT_SLEEP)
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        coVerify(exactly = 0) { repository.updateRecord(any()) }
    }
}
