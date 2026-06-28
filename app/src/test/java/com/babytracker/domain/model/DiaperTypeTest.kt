package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DiaperTypeTest {
    @Test
    fun `parses enum names`() {
        assertEquals(DiaperType.WET, "WET".toDiaperTypeSafe())
        assertEquals(DiaperType.DIRTY, "DIRTY".toDiaperTypeSafe())
        assertEquals(DiaperType.BOTH, "BOTH".toDiaperTypeSafe())
    }

    @Test
    fun `safe defaults to WET for unknown`() {
        assertEquals(DiaperType.WET, "garbage".toDiaperTypeSafe())
    }
}
