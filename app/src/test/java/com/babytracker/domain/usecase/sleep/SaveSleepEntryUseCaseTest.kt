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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

class SaveSleepEntryUseCaseTest {

    private lateinit var repository: SleepRepository
    private lateinit var useCase: SaveSleepEntryUseCase

    @BeforeEach
    fun setUp() {
        repository = mockk()
        useCase = SaveSleepEntryUseCase(repository)
        coEvery { repository.getActiveRecord() } returns null
        coEvery { repository.getCompletedRecordsBetween(any(), any()) } returns emptyList()
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
        assertEquals(ZoneId.systemDefault().id, slot.captured.timezoneId)
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

    @Test
    fun `invoke throws and skips insert when endTime is not after startTime`() = runTest {
        val start = Instant.parse("2026-04-08T09:30:00Z")

        val thrown = runCatching { useCase(start, start, SleepType.NAP) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        coVerify(exactly = 0) { repository.insertRecord(any()) }
    }

    @Test
    fun `invoke throws when nap duration exceeds the max nap duration`() = runTest {
        val start = Instant.parse("2026-04-08T09:00:00Z")
        val end = start.plus(Duration.ofHours(5))

        val thrown = runCatching { useCase(start, end, SleepType.NAP) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        coVerify(exactly = 0) { repository.insertRecord(any()) }
    }

    @Test
    fun `invoke throws when a new night sleep overlaps an existing completed night sleep`() = runTest {
        val start = Instant.parse("2026-04-08T21:00:00Z")
        val end = Instant.parse("2026-04-09T05:00:00Z")
        val existing = SleepRecord(
            id = 1L,
            startTime = Instant.parse("2026-04-08T20:00:00Z"),
            endTime = Instant.parse("2026-04-09T02:00:00Z"),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        coEvery { repository.getCompletedRecordsBetween(start, end) } returns listOf(existing)

        val thrown = runCatching { useCase(start, end, SleepType.NIGHT_SLEEP) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        coVerify(exactly = 0) { repository.insertRecord(any()) }
    }

    @Test
    fun `invoke throws when a new night sleep overlaps the active in-progress record`() = runTest {
        val start = Instant.parse("2026-04-08T21:00:00Z")
        val end = Instant.parse("2026-04-09T05:00:00Z")
        val active = SleepRecord(
            id = 2L,
            startTime = Instant.parse("2026-04-08T22:00:00Z"),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        coEvery { repository.getActiveRecord() } returns active

        val thrown = runCatching { useCase(start, end, SleepType.NIGHT_SLEEP) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        coVerify(exactly = 0) { repository.insertRecord(any()) }
    }

    @Test
    fun `invoke throws when a new nap overlaps an existing nap`() = runTest {
        // Regression coverage for issue #748: getCompletedRecordsBetween returns candidates of
        // every sleep type, so a nap-over-nap must be rejected the same as a night-vs-night one.
        val start = Instant.parse("2026-04-08T13:30:00Z")
        val end = Instant.parse("2026-04-08T14:30:00Z")
        val existingNap = SleepRecord(
            id = 3L,
            startTime = Instant.parse("2026-04-08T13:00:00Z"),
            endTime = Instant.parse("2026-04-08T14:00:00Z"),
            sleepType = SleepType.NAP,
        )
        coEvery { repository.getCompletedRecordsBetween(start, end) } returns listOf(existingNap)

        val thrown = runCatching { useCase(start, end, SleepType.NAP) }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
        coVerify(exactly = 0) { repository.insertRecord(any()) }
    }
}
