package com.babytracker.domain.trends

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class TrendWindowTest {
    @Test
    fun `trendWindowDates returns days dates oldest first inclusive of today`() {
        val today = LocalDate.of(2026, 6, 14)
        val dates = trendWindowDates(today, 7)
        assertEquals(7, dates.size)
        assertEquals(LocalDate.of(2026, 6, 8), dates.first())
        assertEquals(today, dates.last())
    }

    @Test
    fun `windowStartInstant is start of the first day in zone`() {
        val zone = ZoneId.of("UTC")
        val start = windowStartInstant(LocalDate.of(2026, 6, 14), 7, zone)
        assertEquals(LocalDate.of(2026, 6, 8).atStartOfDay(zone).toInstant(), start)
    }
}
