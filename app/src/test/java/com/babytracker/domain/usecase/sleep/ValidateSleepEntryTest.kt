package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ValidateSleepEntryTest {

    private val now = Instant.parse("2026-04-09T12:00:00Z")

    @Test
    fun `returns null for a valid nap`() {
        val start = Instant.parse("2026-04-09T09:00:00Z")
        val end = Instant.parse("2026-04-09T10:00:00Z")

        val result = validateSleepEntry(start, end, SleepType.NAP, emptyList(), now)

        assertNull(result)
    }

    @Test
    fun `returns END_BEFORE_START when end equals start`() {
        val time = Instant.parse("2026-04-09T09:00:00Z")

        val result = validateSleepEntry(time, time, SleepType.NAP, emptyList(), now)

        assertEquals(SleepEntryError.END_BEFORE_START, result)
    }

    @Test
    fun `returns END_BEFORE_START when end is before start`() {
        val start = Instant.parse("2026-04-09T09:00:00Z")
        val end = start.minusSeconds(60)

        val result = validateSleepEntry(start, end, SleepType.NAP, emptyList(), now)

        assertEquals(SleepEntryError.END_BEFORE_START, result)
    }

    @Test
    fun `returns DURATION_TOO_LONG for a nap longer than the max nap duration`() {
        val start = Instant.parse("2026-04-09T09:00:00Z")
        val end = start.plus(maxSleepDurationFor(SleepType.NAP)).plusSeconds(1)

        val result = validateSleepEntry(start, end, SleepType.NAP, emptyList(), now)

        assertEquals(SleepEntryError.DURATION_TOO_LONG, result)
    }

    @Test
    fun `returns DURATION_TOO_LONG for a night sleep longer than the max night sleep duration`() {
        val start = Instant.parse("2026-04-09T09:00:00Z")
        val end = start.plus(maxSleepDurationFor(SleepType.NIGHT_SLEEP)).plusSeconds(1)

        val result = validateSleepEntry(start, end, SleepType.NIGHT_SLEEP, emptyList(), now)

        assertEquals(SleepEntryError.DURATION_TOO_LONG, result)
    }

    @Test
    fun `returns null at exactly the max duration boundary`() {
        val start = Instant.parse("2026-04-09T09:00:00Z")
        val end = start.plus(maxSleepDurationFor(SleepType.NAP))

        val result = validateSleepEntry(start, end, SleepType.NAP, emptyList(), now)

        assertNull(result)
    }

    @Test
    fun `does not check overlap for naps`() {
        val existingNight = SleepRecord(
            id = 1L,
            startTime = Instant.parse("2026-04-09T00:00:00Z"),
            endTime = Instant.parse("2026-04-09T06:00:00Z"),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        // Overlapping window, but as a NAP it is exempt from the night-overlap rule.
        val start = Instant.parse("2026-04-09T02:00:00Z")
        val end = Instant.parse("2026-04-09T03:00:00Z")

        val result = validateSleepEntry(start, end, SleepType.NAP, listOf(existingNight), now)

        assertNull(result)
    }

    @Test
    fun `returns NIGHT_SLEEP_OVERLAP when a new night sleep overlaps a completed one`() {
        val existing = SleepRecord(
            id = 1L,
            startTime = Instant.parse("2026-04-08T22:00:00Z"),
            endTime = Instant.parse("2026-04-09T06:00:00Z"),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        val start = Instant.parse("2026-04-09T05:00:00Z")
        val end = Instant.parse("2026-04-09T07:00:00Z")

        val result = validateSleepEntry(start, end, SleepType.NIGHT_SLEEP, listOf(existing), now)

        assertEquals(SleepEntryError.NIGHT_SLEEP_OVERLAP, result)
    }

    @Test
    fun `returns NIGHT_SLEEP_OVERLAP against an in-progress record using now as its end`() {
        val active = SleepRecord(
            id = 1L,
            startTime = Instant.parse("2026-04-09T10:00:00Z"),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        // Starts before `now` (the in-progress record's presumed end) so it overlaps.
        val start = now.minusSeconds(3600)
        val end = now.plusSeconds(3600)

        val result = validateSleepEntry(start, end, SleepType.NIGHT_SLEEP, listOf(active), now)

        assertEquals(SleepEntryError.NIGHT_SLEEP_OVERLAP, result)
    }

    @Test
    fun `returns null against an in-progress record when the new entry ends before now`() {
        val active = SleepRecord(
            id = 1L,
            startTime = Instant.parse("2026-04-09T10:00:00Z"),
            endTime = null,
            sleepType = SleepType.NIGHT_SLEEP,
        )
        val start = Instant.parse("2026-04-08T20:00:00Z")
        val end = Instant.parse("2026-04-08T23:00:00Z")

        val result = validateSleepEntry(start, end, SleepType.NIGHT_SLEEP, listOf(active), now)

        assertNull(result)
    }

    @Test
    fun `excludes the record being edited from the overlap check`() {
        val existing = SleepRecord(
            id = 7L,
            startTime = Instant.parse("2026-04-08T22:00:00Z"),
            endTime = Instant.parse("2026-04-09T06:00:00Z"),
            sleepType = SleepType.NIGHT_SLEEP,
        )
        val start = Instant.parse("2026-04-08T22:00:00Z")
        val end = Instant.parse("2026-04-09T07:00:00Z")

        val result = validateSleepEntry(start, end, SleepType.NIGHT_SLEEP, listOf(existing), now, excludingId = 7L)

        assertNull(result)
    }

    @Test
    fun `does not check overlap against naps even when night sleep is being validated`() {
        val nap = SleepRecord(
            id = 3L,
            startTime = Instant.parse("2026-04-09T02:00:00Z"),
            endTime = Instant.parse("2026-04-09T03:00:00Z"),
            sleepType = SleepType.NAP,
        )
        val start = Instant.parse("2026-04-09T02:30:00Z")
        val end = Instant.parse("2026-04-09T04:00:00Z")

        val result = validateSleepEntry(start, end, SleepType.NIGHT_SLEEP, listOf(nap), now)

        assertNull(result)
    }
}
