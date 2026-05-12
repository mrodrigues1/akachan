package com.babytracker.ui.partner

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant

class PartnerDashboardTimeTest {

    @Test
    fun `baby age weeks uses injected dashboard time`() {
        val now = Instant.parse("2026-05-12T06:00:00Z")
        val birthDateMs = now.minus(Duration.ofDays(35)).toEpochMilli()

        assertEquals(5, babyAgeWeeks(birthDateMs = birthDateMs, now = now))
    }

    @Test
    fun `baby age subtitle uses readable week copy`() {
        assertEquals("Less than 1 week old, read-only partner view", babyAgeSubtitleText(0))
        assertEquals("1 week old, read-only partner view", babyAgeSubtitleText(1))
        assertEquals("5 weeks old, read-only partner view", babyAgeSubtitleText(5))
    }
}
