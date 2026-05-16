package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
    fun `duration subtracts pausedDurationMs and floors at zero`() {
        val end = start.plus(Duration.ofMinutes(20))
        val session = PumpingSession(
            startTime = start,
            endTime = end,
            breast = PumpingBreast.BOTH,
            pausedDurationMs = Duration.ofMinutes(5).toMillis(),
        )
        assertEquals(Duration.ofMinutes(15), session.duration)
    }

    @Test
    fun `duration null when in progress`() {
        val session = PumpingSession(startTime = start, breast = PumpingBreast.LEFT)
        assertEquals(null, session.duration)
    }

    @Test
    fun `activeDurationUntil uses provided clock when in progress`() {
        val session = PumpingSession(startTime = start, breast = PumpingBreast.LEFT)
        val now = start.plus(Duration.ofMinutes(8))
        assertEquals(Duration.ofMinutes(8), session.activeDurationUntil(now))
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
