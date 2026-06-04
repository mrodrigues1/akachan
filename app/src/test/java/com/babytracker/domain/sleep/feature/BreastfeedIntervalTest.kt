package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionTuning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedIntervalTest {
    @Test
    fun `valid completed session returns interval`() {
        val result = BreastfeedInterval.from(session(1_000L, 4_600L))

        assertTrue(result != null)
        assertFalse(result!!.isActive)
        assertTrue(result.isCompleted)
        assertEquals(3_600L, result.durationMillis)
    }

    @Test
    fun `session end before start returns null`() {
        assertNull(BreastfeedInterval.from(session(5_000L, 3_000L)))
    }

    @Test
    fun `session end equals start returns null`() {
        assertNull(BreastfeedInterval.from(session(5_000L, 5_000L)))
    }

    @Test
    fun `completed session longer than maximum returns null`() {
        val durationMillis = SleepPredictionTuning.MAX_FEED_DURATION_HOURS * 3_600_000L + 1

        assertNull(BreastfeedInterval.from(session(1_000L, 1_000L + durationMillis)))
    }

    @Test
    fun `open session returns active interval`() {
        val result = BreastfeedInterval.from(session(1_000L, null))

        assertTrue(result != null)
        assertTrue(result!!.isActive)
        assertNull(result.endMillis)
        assertNull(result.durationMillis)
    }

    private fun session(startMillis: Long, endMillis: Long?): BreastfeedingSession =
        BreastfeedingSession(
            startTime = Instant.ofEpochMilli(startMillis),
            endTime = endMillis?.let { Instant.ofEpochMilli(it) },
            startingSide = BreastSide.LEFT,
        )
}
