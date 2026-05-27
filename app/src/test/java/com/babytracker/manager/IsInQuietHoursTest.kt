package com.babytracker.manager

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId

class IsInQuietHoursTest {

    private val utc = ZoneId.of("UTC")

    // 2024-01-01T02:00:00Z → 02:00 UTC → minuteOfDay = 120
    private val t0200 = Instant.parse("2024-01-01T02:00:00Z")

    // 2024-01-01T07:00:00Z → 07:00 UTC → minuteOfDay = 420
    private val t0700 = Instant.parse("2024-01-01T07:00:00Z")

    // 2024-01-01T22:00:00Z → 22:00 UTC → minuteOfDay = 1320
    private val t2200 = Instant.parse("2024-01-01T22:00:00Z")

    // 2024-01-01T12:00:00Z → 12:00 UTC → minuteOfDay = 720
    private val t1200 = Instant.parse("2024-01-01T12:00:00Z")

    @Test
    fun `start equals end returns false (disabled)`() {
        assertFalse(isInQuietHours(t0200, 0, 0, utc))
        assertFalse(isInQuietHours(t0200, 480, 480, utc))
    }

    @Test
    fun `non-wrapping window contains instant`() {
        // quiet 00:00–08:00 (0–480), 02:00 is inside
        assertTrue(isInQuietHours(t0200, 0, 480, utc))
    }

    @Test
    fun `non-wrapping window does not contain instant`() {
        // quiet 00:00–08:00 (0–480), 12:00 is outside
        assertFalse(isInQuietHours(t1200, 0, 480, utc))
    }

    @Test
    fun `non-wrapping window boundary - instant at start is inside`() {
        // quiet 00:00–08:00 (0–480), 00:00 (minuteOfDay=0) is inside (in 0 until 480)
        val t0000 = Instant.parse("2024-01-01T00:00:00Z")
        assertTrue(isInQuietHours(t0000, 0, 480, utc))
    }

    @Test
    fun `non-wrapping window boundary - instant at end is outside`() {
        // quiet 00:00–08:00 (0–480), 08:00 (minuteOfDay=480) is outside (not in 0 until 480)
        val t0800 = Instant.parse("2024-01-01T08:00:00Z")
        assertFalse(isInQuietHours(t0800, 0, 480, utc))
    }

    @Test
    fun `wrapping window contains instant after start`() {
        // quiet 22:00–07:00 (1320–420), 22:00 (1320) is inside
        assertTrue(isInQuietHours(t2200, 1320, 420, utc))
    }

    @Test
    fun `wrapping window contains instant before end`() {
        // quiet 22:00–07:00 (1320–420), 02:00 (120) is inside
        assertTrue(isInQuietHours(t0200, 1320, 420, utc))
    }

    @Test
    fun `wrapping window does not contain instant in the middle of the day`() {
        // quiet 22:00–07:00 (1320–420), 12:00 (720) is outside
        assertFalse(isInQuietHours(t1200, 1320, 420, utc))
    }

    @Test
    fun `wrapping window boundary - instant at exact end is outside`() {
        // quiet 22:00–07:00 (1320–420), 07:00 (420) is outside (< 420 is false for 420)
        assertFalse(isInQuietHours(t0700, 1320, 420, utc))
    }
}
