package com.babytracker.domain.usecase.trends

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.trends.TrendRange
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class GetSleepDurationTrendUseCaseTest {
    private val clock = Clock.fixed(Instant.parse("2026-06-14T12:00:00Z"), ZoneOffset.UTC)
    private lateinit var sleep: SleepRepository
    private lateinit var useCase: GetSleepDurationTrendUseCase

    private fun record(start: String, endHoursLater: Long, type: SleepType) = SleepRecord(
        id = 0,
        startTime = Instant.parse(start),
        endTime = Instant.parse(start).plusSeconds(endHoursLater * 3600),
        sleepType = type,
    )

    @BeforeEach
    fun setup() {
        sleep = mockk()
        useCase = GetSleepDurationTrendUseCase(sleep, clock)
    }

    @Test
    fun `splits night and nap hours by start day`() = runTest {
        // All starts are before the fixed clock's "now" (12:00Z) so none are dropped as future.
        coEvery { sleep.getCompletedRecordsSince(any()) } returns listOf(
            record("2026-06-14T01:00:00Z", 8, SleepType.NIGHT_SLEEP),
            record("2026-06-14T08:00:00Z", 2, SleepType.NAP),
            record("2026-06-14T10:00:00Z", 1, SleepType.NAP),
        )

        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }

        assertEquals(8.0, day.nightHours, 0.001)
        assertEquals(3.0, day.napHours, 0.001)
        assertEquals(11.0, day.totalHours, 0.001)
    }

    @Test
    fun `days with no sleep are zero`() = runTest {
        coEvery { sleep.getCompletedRecordsSince(any()) } returns emptyList()
        val result = useCase(TrendRange.SEVEN_DAYS)
        assertEquals(7, result.size)
        assertEquals(0.0, result.sumOf { it.totalHours }, 0.001)
    }

    @Test
    fun `future-dated sleep on today is excluded`() = runTest {
        // clock now = 2026-06-14T12:00Z; this record starts at 14:00 today (after now).
        coEvery { sleep.getCompletedRecordsSince(any()) } returns listOf(
            record("2026-06-14T14:00:00Z", 1, SleepType.NAP),
        )
        val day = useCase(TrendRange.SEVEN_DAYS).first { it.date.toString() == "2026-06-14" }
        assertEquals(0.0, day.totalHours, 0.001)
    }
}
