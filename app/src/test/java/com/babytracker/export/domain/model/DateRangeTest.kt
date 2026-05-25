package com.babytracker.export.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class DateRangeTest {

    @Test
    fun `lastDays builds an inclusive window ending now`() {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        val range = DateRange.lastDays(7, now)
        assertEquals(now, range.end)
        assertEquals(Duration.ofDays(7), Duration.between(range.start, range.end))
    }

    @Test
    fun `custom rejects start after end`() {
        val now = Instant.parse("2026-05-24T12:00:00Z")
        assertThrows(IllegalArgumentException::class.java) {
            DateRange(start = now, end = now.minusSeconds(1))
        }
    }
}
