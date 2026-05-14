package com.babytracker.ui.sleep

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FormatAgeTest {

    @Test
    fun `weeks under one month shows weeks only`() {
        assertEquals("0 weeks old", formatAge(0))
        assertEquals("3 weeks old", formatAge(3))
        assertEquals("4 weeks old", formatAge(4))
    }

    @Test
    fun `52 weeks renders as 12 months not 13`() {
        // Regression: previous (ageInWeeks / 4) returned 13.
        assertEquals("12 months old", formatAge(52))
    }

    @Test
    fun `26 weeks renders as 6 months exactly`() {
        // Regression: previous (26 / 4) returned 6 months + 2 weeks.
        assertEquals("6 months old", formatAge(26))
    }

    @Test
    fun `13 weeks renders as 3 months exactly`() {
        // Regression: previous (13 / 4) returned 3 months + 1 week.
        assertEquals("3 months old", formatAge(13))
    }

    @Test
    fun `non-aligned weeks include remaining weeks`() {
        // 8 weeks = 56 days; 56/30 = 1 month, remainder 26 days = 3 weeks.
        assertEquals("1 months, 3 weeks old", formatAge(8))
    }

    @Test
    fun `5 weeks crosses the one-month boundary`() {
        // 35 days / 30 = 1 month, remainder 5 days = 0 weeks.
        assertEquals("1 months old", formatAge(5))
    }
}
