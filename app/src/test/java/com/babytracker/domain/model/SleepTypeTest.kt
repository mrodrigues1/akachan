package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SleepTypeTest {

    @Test
    fun `parses by enum name`() {
        assertEquals(SleepType.NAP, "NAP".toSleepTypeOrNull())
        assertEquals(SleepType.NIGHT_SLEEP, "NIGHT_SLEEP".toSleepTypeOrNull())
    }

    @Test
    fun `does not accept a display label as an alias`() {
        assertNull("Nap".toSleepTypeOrNull())
        assertNull("Night Sleep".toSleepTypeOrNull())
    }

    @Test
    fun `rejects an unrecognized value`() {
        assertNull("night_sleep".toSleepTypeOrNull())
        assertNull("garbage".toSleepTypeOrNull())
    }

    @Test
    fun `toSleepTypeSafe falls back to NAP for anything unrecognized`() {
        assertEquals(SleepType.NAP, "garbage".toSleepTypeSafe())
        assertEquals(SleepType.NIGHT_SLEEP, "NIGHT_SLEEP".toSleepTypeSafe())
    }

    @Test
    fun `toSleepTypeWithLegacyLabelOrNull recognizes the removed display labels`() {
        assertEquals(SleepType.NAP, "Nap".toSleepTypeWithLegacyLabelOrNull())
        assertEquals(SleepType.NIGHT_SLEEP, "Night Sleep".toSleepTypeWithLegacyLabelOrNull())
        assertNull("garbage".toSleepTypeWithLegacyLabelOrNull())
    }

    @Test
    fun `toSleepTypeSafe recognizes a legacy label instead of miscategorizing it as NAP`() {
        assertEquals(SleepType.NIGHT_SLEEP, "Night Sleep".toSleepTypeSafe())
    }
}
