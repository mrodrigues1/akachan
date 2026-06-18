package com.babytracker.domain.usecase.pumping

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ValidatePumpingEditTest {

    private val now = Instant.parse("2026-05-16T10:00:00Z")
    private val start = Instant.parse("2026-05-16T09:00:00Z")

    @Test
    fun returnsNullForValidCompleteSession() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = start.plusSeconds(1800),
            volumeMl = 100,
            pausedDurationMs = 60_000L,
            now = now,
        )
        assertNull(result)
    }

    @Test
    fun returnsNullWhenEndTimeIsNull() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = null,
            volumeMl = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertNull(result)
    }

    @Test
    fun returnsErrorWhenEndEqualsStart() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = start,
            volumeMl = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals(PumpingEditError.END_BEFORE_START, result)
    }

    @Test
    fun returnsErrorWhenEndBeforeStart() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = start.minusSeconds(60),
            volumeMl = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals(PumpingEditError.END_BEFORE_START, result)
    }

    @Test
    fun returnsErrorWhenEndInFuture() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = now.plusSeconds(60),
            volumeMl = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals(PumpingEditError.END_IN_FUTURE, result)
    }

    @Test
    fun returnsErrorWhenPausedTimeExceedsSessionLength() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = start.plusSeconds(600),
            volumeMl = null,
            pausedDurationMs = 700_000L,
            now = now,
        )
        assertEquals(PumpingEditError.PAUSE_EXCEEDS_SESSION, result)
    }

    @Test
    fun returnsErrorWhenVolumeIsZero() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = start.plusSeconds(1800),
            volumeMl = 0,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals(PumpingEditError.VOLUME_NOT_POSITIVE, result)
    }

    @Test
    fun returnsErrorWhenVolumeIsNegative() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = start.plusSeconds(1800),
            volumeMl = -1,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals(PumpingEditError.VOLUME_NOT_POSITIVE, result)
    }

    @Test
    fun endBeforeStartCheckedBeforeFutureCheck() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = start.minusSeconds(10),
            volumeMl = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals(PumpingEditError.END_BEFORE_START, result)
    }

    @Test
    fun returnsNullWhenVolumeIsNullAndSessionValid() {
        val result = validatePumpingEdit(
            startTime = start,
            endTime = start.plusSeconds(900),
            volumeMl = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertNull(result)
    }

    @Test
    fun returnsErrorWhenStartIsInFuture() {
        val result = validatePumpingEdit(
            startTime = now.plusSeconds(60),
            endTime = null,
            volumeMl = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals(PumpingEditError.START_IN_FUTURE, result)
    }

    @Test
    fun returnsErrorWhenStartIsInFutureEvenWithNullEnd() {
        val futureStart = now.plusSeconds(3600)
        val result = validatePumpingEdit(
            startTime = futureStart,
            endTime = null,
            volumeMl = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals(PumpingEditError.START_IN_FUTURE, result)
    }
}
