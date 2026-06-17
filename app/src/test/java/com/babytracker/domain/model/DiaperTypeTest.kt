package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class DiaperTypeTest {
    @Test
    fun `parses enum names`() {
        assertEquals(DiaperType.WET, "WET".toDiaperTypeOrNull())
        assertEquals(DiaperType.DIRTY, "DIRTY".toDiaperTypeOrNull())
        assertEquals(DiaperType.BOTH, "BOTH".toDiaperTypeOrNull())
    }

    @Test
    fun `parses human labels`() {
        assertEquals(DiaperType.WET, "Wet".toDiaperTypeOrNull())
        assertEquals(DiaperType.BOTH, "Both".toDiaperTypeOrNull())
    }

    @Test
    fun `returns null for unknown`() {
        assertNull("nope".toDiaperTypeOrNull())
    }

    @Test
    fun `safe defaults to WET`() {
        assertEquals(DiaperType.WET, "garbage".toDiaperTypeSafe())
    }
}
