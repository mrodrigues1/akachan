package com.babytracker.ui.partner

import com.babytracker.sharing.domain.model.SessionSnapshot
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

    @Test
    fun `active feeding duration estimates from current partner time`() {
        val startedAt = Instant.parse("2026-05-12T20:15:00Z")
        val now = Instant.parse("2026-05-12T20:18:06Z")
        val session = SessionSnapshot(
            id = 1L,
            startTime = startedAt.toEpochMilli(),
            endTime = null,
            startingSide = "LEFT",
            switchTime = null,
            pausedDurationMs = 0L,
            notes = null,
        )

        assertEquals(
            Duration.ofMinutes(3).plusSeconds(6),
            activeSessionElapsedDuration(
                session = session,
                lastSyncAt = startedAt,
                now = now,
            ),
        )
    }

    @Test
    fun `stale active feeding duration stays frozen at last shared update`() {
        val lastSyncAt = Instant.parse("2026-05-12T20:00:00Z")
        val now = Instant.parse("2026-05-12T20:31:00Z")
        val session = SessionSnapshot(
            id = 1L,
            startTime = lastSyncAt.minus(Duration.ofHours(1)).toEpochMilli(),
            endTime = null,
            startingSide = "LEFT",
            switchTime = null,
            pausedDurationMs = 0L,
            notes = null,
        )

        assertEquals(
            Duration.ofHours(1),
            activeSessionElapsedDuration(
                session = session,
                lastSyncAt = lastSyncAt,
                now = now,
            ),
        )
    }
}
