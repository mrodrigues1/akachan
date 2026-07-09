package com.babytracker.ui.sleep

import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class SleepTodayStatsTest {

    private val zone = ZoneOffset.UTC
    private val today = LocalDate.of(2026, 4, 6)

    private fun record(
        id: Long,
        start: String,
        end: String?,
        type: SleepType = SleepType.NAP,
    ) = SleepRecord(
        id = id,
        startTime = Instant.parse(start),
        endTime = end?.let { Instant.parse(it) },
        sleepType = type,
    )

    @Test
    fun `empty history yields empty stats`() {
        assertEquals(SleepTodayStats(), sleepTodayStats(emptyList(), today, zone))
    }

    @Test
    fun `entries keep only completed records started today, newest first`() {
        val yesterdayNap = record(1, "2026-04-05T10:00:00Z", "2026-04-05T11:00:00Z")
        val inProgress = record(2, "2026-04-06T14:00:00Z", null)
        val morningNap = record(3, "2026-04-06T09:00:00Z", "2026-04-06T10:00:00Z")
        val afternoonNap = record(4, "2026-04-06T13:00:00Z", "2026-04-06T13:30:00Z")

        val stats = sleepTodayStats(listOf(yesterdayNap, inProgress, morningNap, afternoonNap), today, zone)

        assertEquals(listOf(afternoonNap, morningNap), stats.entries)
        assertEquals(Duration.ofMinutes(90), stats.totalSleep)
        assertEquals(2, stats.napCount)
    }

    @Test
    fun `night sleep counts records that ended today even when started yesterday`() {
        val lastNight = record(1, "2026-04-05T20:00:00Z", "2026-04-06T05:00:00Z", SleepType.NIGHT_SLEEP)

        val stats = sleepTodayStats(listOf(lastNight), today, zone)

        // Started yesterday, so it is not one of today's entries — but its 9h still count as night sleep.
        assertEquals(emptyList<SleepRecord>(), stats.entries)
        assertEquals(Duration.ofHours(9), stats.nightSleep)
    }

    @Test
    fun `naps do not count toward night sleep`() {
        val nap = record(1, "2026-04-06T09:00:00Z", "2026-04-06T10:00:00Z")

        val stats = sleepTodayStats(listOf(nap), today, zone)

        assertEquals(Duration.ZERO, stats.nightSleep)
        assertEquals(Duration.ofHours(1), stats.totalSleep)
    }

    @Test
    fun `totalSleep merges overlapping records instead of double-counting`() {
        // Legacy/bypassed-validation data: two records for today genuinely overlap. The real
        // elapsed span is 09:00-11:00 (2h), not the naive 1h + 1.5h = 2h30m sum.
        val first = record(1, "2026-04-06T09:00:00Z", "2026-04-06T10:00:00Z")
        val overlapping = record(2, "2026-04-06T09:30:00Z", "2026-04-06T11:00:00Z", SleepType.NAP)

        val stats = sleepTodayStats(listOf(first, overlapping), today, zone)

        assertEquals(Duration.ofHours(2), stats.totalSleep)
    }

    @Test
    fun `nightSleep merges overlapping night sleep records instead of double-counting`() {
        val first = record(1, "2026-04-05T20:00:00Z", "2026-04-06T02:00:00Z", SleepType.NIGHT_SLEEP)
        val overlapping = record(2, "2026-04-06T01:00:00Z", "2026-04-06T05:00:00Z", SleepType.NIGHT_SLEEP)

        val stats = sleepTodayStats(listOf(first, overlapping), today, zone)

        // Real elapsed span is 2026-04-05T20:00 to 2026-04-06T05:00 = 9h, not the naive 6h + 4h = 10h.
        assertEquals(Duration.ofHours(9), stats.nightSleep)
    }
}
