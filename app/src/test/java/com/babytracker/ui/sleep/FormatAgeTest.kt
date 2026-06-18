package com.babytracker.ui.sleep

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatAgeTest {

    @Test
    fun `weeks under one month decompose to zero months`() {
        assertEquals(0 to 0, ageBreakdown(0))
        assertEquals(0 to 3, ageBreakdown(3))
        assertEquals(0 to 4, ageBreakdown(4))
    }

    @Test
    fun `52 weeks decomposes to 12 months not 13`() {
        // Regression: previous (ageInWeeks / 4) returned 13.
        assertEquals(12 to 0, ageBreakdown(52))
    }

    @Test
    fun `26 weeks decomposes to 6 months exactly`() {
        // Regression: previous (26 / 4) returned 6 months + 2 weeks.
        assertEquals(6 to 0, ageBreakdown(26))
    }

    @Test
    fun `13 weeks decomposes to 3 months exactly`() {
        // Regression: previous (13 / 4) returned 3 months + 1 week.
        assertEquals(3 to 0, ageBreakdown(13))
    }

    @Test
    fun `non-aligned weeks include remaining weeks`() {
        // 8 weeks = 56 days; 56/30 = 1 month, remainder 26 days = 3 weeks.
        assertEquals(1 to 3, ageBreakdown(8))
    }

    @Test
    fun `5 weeks crosses the one-month boundary`() {
        // 35 days / 30 = 1 month, remainder 5 days = 0 weeks.
        assertEquals(1 to 0, ageBreakdown(5))
    }
}
