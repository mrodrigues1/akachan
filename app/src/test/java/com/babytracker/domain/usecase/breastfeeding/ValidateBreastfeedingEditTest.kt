package com.babytracker.domain.usecase.breastfeeding

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class ValidateBreastfeedingEditTest {

    private val now = Instant.parse("2026-05-15T10:00:00Z")
    private val start = Instant.parse("2026-05-15T09:00:00Z")

    @Test
    fun returnsNullWhenEndAfterStartAndPauseFits() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = start.plusSeconds(1800),
            pausedDurationMs = 60_000L,
            now = now,
        )
        assertNull(result)
    }

    @Test
    fun returnsNullWhenEndTimeIsNull() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertNull(result)
    }

    @Test
    fun returnsErrorWhenEndEqualsStart() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = start,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("End time must be after start time", result)
    }

    @Test
    fun returnsErrorWhenEndBeforeStart() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = start.minusSeconds(60),
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("End time must be after start time", result)
    }

    @Test
    fun returnsErrorWhenEndInFuture() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = now.plusSeconds(60),
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("End time can't be in the future", result)
    }

    @Test
    fun returnsErrorWhenStartInFuture() {
        val result = validateBreastfeedingEdit(
            startTime = now.plusSeconds(60),
            endTime = null,
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("Start time can't be in the future", result)
    }

    @Test
    fun returnsErrorWhenPausedDurationExceedsSessionLength() {
        val result = validateBreastfeedingEdit(
            startTime = start,
            endTime = start.plusSeconds(600),
            pausedDurationMs = 700_000L,
            now = now,
        )
        assertEquals("Session is shorter than recorded pauses", result)
    }

    @Test
    fun futureEndChecksBeforeStartOrderCheck() {
        // both wrong; future-end error wins so user sees the more relevant message
        val result = validateBreastfeedingEdit(
            startTime = now.plusSeconds(120),
            endTime = now.plusSeconds(60),
            pausedDurationMs = 0L,
            now = now,
        )
        assertEquals("Start time can't be in the future", result)
    }
}
