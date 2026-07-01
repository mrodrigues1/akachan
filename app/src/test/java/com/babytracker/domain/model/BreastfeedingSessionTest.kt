package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class BreastfeedingSessionTest {

    private val start: Instant = Instant.parse("2026-04-06T08:00:00Z")

    @Test
    fun `recommendedNextSide is null while session is in progress`() {
        val session = BreastfeedingSession(startTime = start, startingSide = BreastSide.LEFT)
        assertNull(session.recommendedNextSide())
    }

    @Test
    fun `recommendedNextSide is opposite side when there was no switch`() {
        val session = BreastfeedingSession(
            startTime = start,
            endTime = start.plus(Duration.ofMinutes(15)),
            startingSide = BreastSide.RIGHT,
        )
        assertEquals(BreastSide.LEFT, session.recommendedNextSide())
    }

    @Test
    fun `recommendedNextSide is second side when it was used less than the first`() {
        val session = BreastfeedingSession(
            startTime = start,
            switchTime = start.plus(Duration.ofMinutes(10)),
            endTime = start.plus(Duration.ofMinutes(14)),
            startingSide = BreastSide.LEFT,
        )
        // LEFT 10m, RIGHT 4m — RIGHT was used less
        assertEquals(BreastSide.RIGHT, session.recommendedNextSide())
    }

    @Test
    fun `recommendedNextSide is starting side when it was used less than the second`() {
        val session = BreastfeedingSession(
            startTime = start,
            switchTime = start.plus(Duration.ofMinutes(4)),
            endTime = start.plus(Duration.ofMinutes(14)),
            startingSide = BreastSide.LEFT,
        )
        // LEFT 4m, RIGHT 10m — LEFT was used less
        assertEquals(BreastSide.LEFT, session.recommendedNextSide())
    }

    @Test
    fun `recommendedNextSide subtracts pause time from the second side`() {
        // LEFT 10m, then RIGHT 12m wall-clock with an 8m pause → RIGHT only 4m of actual feeding.
        // A pause-blind comparison would pick LEFT (12m > 10m); the pause-aware one picks RIGHT.
        val session = BreastfeedingSession(
            startTime = start,
            switchTime = start.plus(Duration.ofMinutes(10)),
            endTime = start.plus(Duration.ofMinutes(22)),
            startingSide = BreastSide.LEFT,
            pausedDurationMs = Duration.ofMinutes(8).toMillis(),
        )
        assertEquals(BreastSide.RIGHT, session.recommendedNextSide())
    }
}
