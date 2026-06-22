package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VaccineStatusTest {
    @Test
    fun `parses enum names`() {
        assertEquals(VaccineStatus.TO_SCHEDULE, "TO_SCHEDULE".toVaccineStatusOrNull())
        assertEquals(VaccineStatus.SCHEDULED, "SCHEDULED".toVaccineStatusOrNull())
        assertEquals(VaccineStatus.ADMINISTERED, "ADMINISTERED".toVaccineStatusOrNull())
    }

    @Test
    fun `returns null for unknown`() {
        assertNull("nope".toVaccineStatusOrNull())
    }

    @Test
    fun `safe defaults to ADMINISTERED`() {
        assertEquals(VaccineStatus.ADMINISTERED, "garbage".toVaccineStatusSafe())
        assertEquals(VaccineStatus.TO_SCHEDULE, "TO_SCHEDULE".toVaccineStatusSafe())
    }
}
