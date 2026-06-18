package com.babytracker.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
class DateTimeExtTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun formatTime12hMorningReturnsAmFormat() {
        val instant = Instant.parse("2026-04-06T08:42:00Z")
        val result = instant.formatTime12h()
        assert(result.contains("AM") || result.contains("PM")) {
            "Expected 12h format with AM/PM, got: $result"
        }
    }

    @Test
    fun formatTime12hAfternoonReturnsPmFormat() {
        val instant = Instant.parse("2026-04-06T15:30:00Z")
        val result = instant.formatTime12h()
        assert(result.contains("AM") || result.contains("PM")) {
            "Expected 12h format with AM/PM, got: $result"
        }
    }

    @Test
    fun groupByLocalDateEmptyListReturnsEmptyMap() {
        val result = emptyList<Instant>().groupByLocalDate { it }
        assertEquals(emptyMap<LocalDate, List<Instant>>(), result)
    }

    @Test
    fun groupByLocalDateSingleDayReturnsOneGroup() {
        val morning = Instant.parse("2026-04-06T12:00:00Z")
        val evening = Instant.parse("2026-04-06T13:00:00Z")
        val result = listOf(morning, evening).groupByLocalDate { it }
        assertEquals(1, result.size)
    }

    @Test
    fun groupByLocalDateTwoDaysReturnsTwoGroups() {
        val day1 = Instant.parse("2026-04-05T10:00:00Z")
        val day2 = Instant.parse("2026-04-06T10:00:00Z")
        val result = listOf(day1, day2).groupByLocalDate { it }
        assertEquals(2, result.size)
    }

    @Test
    fun localDateToRelativeLabelTodayReturnsToday() {
        val today = LocalDate.now()
        assertEquals("Today", today.toRelativeLabel("Today", "Yesterday"))
    }

    @Test
    fun localDateToRelativeLabelYesterdayReturnsYesterday() {
        val yesterday = LocalDate.now().minusDays(1)
        assertEquals("Yesterday", yesterday.toRelativeLabel("Today", "Yesterday"))
    }

    @Test
    fun localDateToRelativeLabelOlderDateReturnsFormattedDate() {
        val date = LocalDate.of(2026, 3, 15)
        val result = date.toRelativeLabel("Today", "Yesterday")
        assertEquals("Mar 15", result)
    }

    @Test
    fun formatElapsedAgoUnderOneMinuteReturnsJustNow() {
        assertEquals("Just now", Duration.ofSeconds(30).formatElapsedAgo(context))
    }

    @Test
    fun formatElapsedAgoExactlyOneMinuteReturnsMinutesAgo() {
        assertEquals("1m ago", Duration.ofMinutes(1).formatElapsedAgo(context))
    }

    @Test
    fun formatElapsedAgoMinutesReturnsMinutesAgo() {
        assertEquals("14m ago", Duration.ofMinutes(14).formatElapsedAgo(context))
    }

    @Test
    fun formatElapsedAgoHoursAndMinutesReturnsHoursAndMinutesAgo() {
        assertEquals("2h 14m ago", Duration.ofHours(2).plus(Duration.ofMinutes(14)).formatElapsedAgo(context))
    }

    @Test
    fun formatElapsedAgoExactHourReturnsZeroMinutes() {
        assertEquals("1h 0m ago", Duration.ofHours(1).formatElapsedAgo(context))
    }

    @Test
    fun formatElapsedShortZeroReturnsZeroMinutes() {
        assertEquals("0m", Duration.ZERO.formatElapsedShort())
    }

    @Test
    fun formatElapsedShortNegativeReturnsZeroMinutes() {
        assertEquals("0m", Duration.ofMinutes(-5).formatElapsedShort())
    }

    @Test
    fun formatElapsedShortMinutesOnlyReturnsMinutesNoPadding() {
        assertEquals("43m", Duration.ofMinutes(43).formatElapsedShort())
    }

    @Test
    fun formatElapsedShortHoursAndMinutesReturnsPaddedMinutes() {
        assertEquals("1h 04m", Duration.ofMinutes(64).formatElapsedShort())
    }

    @Test
    fun formatElapsedShortExactHoursReturnsZeroPaddedMinutes() {
        assertEquals("2h 00m", Duration.ofHours(2).formatElapsedShort())
    }

    @Test
    fun formatElapsedCompactUnderOneMinuteReturnsJustNow() {
        assertEquals("Just now", Duration.ofSeconds(30).formatElapsedCompact(context))
    }

    @Test
    fun formatElapsedCompactMinutesReturnsMinutes() {
        assertEquals("14m", Duration.ofMinutes(14).formatElapsedCompact(context))
    }

    @Test
    fun formatElapsedCompactHoursAndMinutesReturnsHoursAndMinutes() {
        assertEquals("2h 14m", Duration.ofHours(2).plus(Duration.ofMinutes(14)).formatElapsedCompact(context))
    }

    @Test
    fun formatElapsedCompactExactDayReturnsDays() {
        assertEquals("1d", Duration.ofDays(1).formatElapsedCompact(context))
    }

    @Test
    fun formatElapsedCompactMultipleDaysDropsHours() {
        assertEquals("7d", Duration.ofHours(174).plus(Duration.ofMinutes(8)).formatElapsedCompact(context))
    }
}
