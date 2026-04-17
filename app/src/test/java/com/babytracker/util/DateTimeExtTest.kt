package com.babytracker.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DateTimeExtTest {

    @Test
    fun `formatTime12h_morning_returnsAmFormat`() {
        val instant = Instant.parse("2026-04-06T08:42:00Z")
        val result = instant.formatTime12h()
        assert(result.contains("AM") || result.contains("PM")) {
            "Expected 12h format with AM/PM, got: $result"
        }
    }

    @Test
    fun `formatTime12h_afternoon_returnsPmFormat`() {
        val instant = Instant.parse("2026-04-06T15:30:00Z")
        val result = instant.formatTime12h()
        assert(result.contains("AM") || result.contains("PM")) {
            "Expected 12h format with AM/PM, got: $result"
        }
    }

    @Test
    fun `groupByLocalDate_emptyList_returnsEmptyMap`() {
        val result = emptyList<Instant>().groupByLocalDate { it }
        assertEquals(emptyMap<LocalDate, List<Instant>>(), result)
    }

    @Test
    fun `groupByLocalDate_singleDay_returnsOneGroup`() {
        val morning = Instant.parse("2026-04-06T12:00:00Z")
        val evening = Instant.parse("2026-04-06T13:00:00Z")
        val result = listOf(morning, evening).groupByLocalDate { it }
        assertEquals(1, result.size)
    }

    @Test
    fun `groupByLocalDate_twoDays_returnsTwoGroups`() {
        val day1 = Instant.parse("2026-04-05T10:00:00Z")
        val day2 = Instant.parse("2026-04-06T10:00:00Z")
        val result = listOf(day1, day2).groupByLocalDate { it }
        assertEquals(2, result.size)
    }

    @Test
    fun `localDateToRelativeLabel_today_returnsToday`() {
        val today = LocalDate.now()
        assertEquals("Today", today.toRelativeLabel())
    }

    @Test
    fun `localDateToRelativeLabel_yesterday_returnsYesterday`() {
        val yesterday = LocalDate.now().minusDays(1)
        assertEquals("Yesterday", yesterday.toRelativeLabel())
    }

    @Test
    fun `localDateToRelativeLabel_olderDate_returnsFormattedDate`() {
        val date = LocalDate.of(2026, 3, 15)
        val result = date.toRelativeLabel()
        assertEquals("Mar 15", result)
    }

    @Test
    fun `formatElapsedAgo_underOneMinute_returnsJustNow`() {
        assertEquals("Just now", Duration.ofSeconds(30).formatElapsedAgo())
    }

    @Test
    fun `formatElapsedAgo_exactlyOneMinute_returnsMinutesAgo`() {
        assertEquals("1m ago", Duration.ofMinutes(1).formatElapsedAgo())
    }

    @Test
    fun `formatElapsedAgo_minutes_returnsMinutesAgo`() {
        assertEquals("14m ago", Duration.ofMinutes(14).formatElapsedAgo())
    }

    @Test
    fun `formatElapsedAgo_hoursAndMinutes_returnsHoursAndMinutesAgo`() {
        assertEquals("2h 14m ago", Duration.ofHours(2).plus(Duration.ofMinutes(14)).formatElapsedAgo())
    }

    @Test
    fun `formatElapsedAgo_exactHour_returnsZeroMinutes`() {
        assertEquals("1h 0m ago", Duration.ofHours(1).formatElapsedAgo())
    }

    @Test
    fun `formatElapsedShort_zero_returnsZeroMinutes`() {
        assertEquals("0m", Duration.ZERO.formatElapsedShort())
    }

    @Test
    fun `formatElapsedShort_negative_returnsZeroMinutes`() {
        assertEquals("0m", Duration.ofMinutes(-5).formatElapsedShort())
    }

    @Test
    fun `formatElapsedShort_minutesOnly_returnsMinutesNoPadding`() {
        assertEquals("43m", Duration.ofMinutes(43).formatElapsedShort())
    }

    @Test
    fun `formatElapsedShort_hoursAndMinutes_returnsPaddedMinutes`() {
        assertEquals("1h 04m", Duration.ofMinutes(64).formatElapsedShort())
    }

    @Test
    fun `formatElapsedShort_exactHours_returnsZeroPaddedMinutes`() {
        assertEquals("2h 00m", Duration.ofHours(2).formatElapsedShort())
    }
}
