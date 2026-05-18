package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PumpingSessionTest {
    private val start = Instant.parse("2026-05-16T10:00:00Z")

    @Test
    fun `isInProgress true when endTime null`() {
        val session = PumpingSession(startTime = start, breast = PumpingBreast.LEFT)
        assertTrue(session.isInProgress)
    }

    @Test
    fun `duration is raw elapsed between start and end`() {
        val end = start.plus(Duration.ofMinutes(20))
        val session = PumpingSession(
            startTime = start,
            endTime = end,
            breast = PumpingBreast.BOTH,
            pausedDurationMs = Duration.ofMinutes(5).toMillis(),
        )
        assertEquals(Duration.ofMinutes(20), session.duration)
    }

    @Test
    fun `duration null when in progress`() {
        val session = PumpingSession(startTime = start, breast = PumpingBreast.LEFT)
        assertNull(session.duration)
    }

    @Test
    fun `activeDuration subtracts pausedDurationMs and floors at zero`() {
        val end = start.plus(Duration.ofMinutes(20))
        val session = PumpingSession(
            startTime = start,
            endTime = end,
            breast = PumpingBreast.BOTH,
            pausedDurationMs = Duration.ofMinutes(5).toMillis(),
        )
        assertEquals(Duration.ofMinutes(15), session.activeDuration)
    }

    @Test
    fun `activeDuration floors at zero when paused exceeds elapsed`() {
        val end = start.plus(Duration.ofMinutes(5))
        val session = PumpingSession(
            startTime = start,
            endTime = end,
            breast = PumpingBreast.LEFT,
            pausedDurationMs = Duration.ofMinutes(10).toMillis(),
        )
        assertEquals(Duration.ZERO, session.activeDuration)
    }

    @Test
    fun `activeDurationUntil uses provided clock when in progress`() {
        val session = PumpingSession(startTime = start, breast = PumpingBreast.LEFT)
        val now = start.plus(Duration.ofMinutes(8))
        assertEquals(Duration.ofMinutes(8), session.activeDurationUntil(now))
    }

    @Test
    fun `activeDurationUntil excludes current pause while in progress`() {
        val pauseStart = start.plus(Duration.ofMinutes(5))
        val now = pauseStart.plus(Duration.ofMinutes(3))
        val session = PumpingSession(
            startTime = start,
            breast = PumpingBreast.LEFT,
            pausedAt = pauseStart,
        )
        assertEquals(Duration.ofMinutes(5), session.activeDurationUntil(now))
    }

    @Test
    fun `isPaused reflects pausedAt`() {
        val session = PumpingSession(
            startTime = start,
            breast = PumpingBreast.RIGHT,
            pausedAt = start.plus(Duration.ofMinutes(2)),
        )
        assertTrue(session.isPaused)
        assertFalse(session.copy(pausedAt = null).isPaused)
    }
}
