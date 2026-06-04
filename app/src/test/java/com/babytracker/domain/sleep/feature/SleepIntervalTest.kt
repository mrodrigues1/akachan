package com.babytracker.domain.sleep.feature

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepPredictionTuning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class SleepIntervalTest {
    private val base = 1_000_000_000L

    @Test
    fun `valid completed nap returns interval`() {
        val durationMillis = Duration.ofHours(1).toMillis()
        val result = SleepInterval.from(base, base + durationMillis, SleepType.NAP)

        assertTrue(result != null)
        assertEquals(base, result!!.startMillis)
        assertEquals(base + durationMillis, result.endMillis)
        assertEquals(durationMillis, result.durationMillis)
        assertTrue(result.isCompleted)
    }

    @Test
    fun `end equals start returns null`() {
        assertNull(SleepInterval.from(base, base, SleepType.NAP))
    }

    @Test
    fun `end before start returns null`() {
        assertNull(SleepInterval.from(base, base - 1, SleepType.NAP))
    }

    @Test
    fun `nap longer than four hours returns null`() {
        assertNull(SleepInterval.from(base, base + Duration.ofHours(4).toMillis() + 1, SleepType.NAP))
    }

    @Test
    fun `night sleep longer than four hours is valid`() {
        val durationMillis = Duration.ofHours(9).toMillis()
        val result = SleepInterval.from(base, base + durationMillis, SleepType.NIGHT_SLEEP)

        assertTrue(result != null)
        assertEquals(durationMillis, result!!.durationMillis)
    }

    @Test
    fun `night sleep longer than maximum returns null`() {
        val durationMillis = Duration.ofHours(SleepPredictionTuning.MAX_NIGHT_SLEEP_DURATION_HOURS).toMillis() + 1

        assertNull(SleepInterval.from(base, base + durationMillis, SleepType.NIGHT_SLEEP))
    }

    @Test
    fun `open record returns open interval`() {
        val result = SleepInterval.from(base, null, SleepType.NAP)

        assertTrue(result != null)
        assertNull(result!!.endMillis)
        assertNull(result.durationMillis)
        assertFalse(result.isCompleted)
    }
}
