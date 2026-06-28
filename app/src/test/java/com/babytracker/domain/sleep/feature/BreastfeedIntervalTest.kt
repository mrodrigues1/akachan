package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionTuning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class BreastfeedIntervalTest {
    @Test
    fun `valid completed session returns interval`() {
        val result = BreastfeedInterval.from(session(1_000L, 4_600L))

        assertTrue(result != null)
        assertEquals(1_000L, result!!.startMillis)
        assertEquals(4_600L, result.endMillis)
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
        assertNull(result!!.endMillis)
    }

    private fun session(startMillis: Long, endMillis: Long?): BreastfeedingSession =
        BreastfeedingSession(
            startTime = Instant.ofEpochMilli(startMillis),
            endTime = endMillis?.let { Instant.ofEpochMilli(it) },
            startingSide = BreastSide.LEFT,
        )
}
