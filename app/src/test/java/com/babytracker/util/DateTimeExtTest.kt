package com.babytracker.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

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

    @Test
    fun formatLongDateUsesLocaleWordOrder() {
        val date = LocalDate.of(2026, 6, 20)
        withLocale(Locale.US) {
            val result = date.formatLongDate()
            assertTrue("Expected English month, got: $result", result.contains("June"))
            assertTrue("Expected year, got: $result", result.contains("2026"))
        }
        withLocale(Locale.forLanguageTag("pt-BR")) {
            val result = date.formatLongDate()
            assertTrue("Expected Portuguese month, got: $result", result.contains("junho"))
            assertTrue("Expected pt-BR connector, got: $result", result.contains(" de "))
        }
    }

    @Test
    fun formatShortTimeHonorsLocaleClock() {
        val afternoon = LocalTime.of(14, 30)
        withLocale(Locale.US) {
            val result = afternoon.formatShortTime()
            assertTrue("Expected 12-hour clock, got: $result", result.contains("2:30"))
            assertFalse("12-hour clock should not show 24h hour, got: $result", result.contains("14"))
        }
        withLocale(Locale.forLanguageTag("pt-BR")) {
            val result = afternoon.formatShortTime()
            assertTrue("Expected 24-hour clock, got: $result", result.contains("14:30"))
            assertFalse("24-hour clock should not show AM/PM, got: $result", result.contains("PM"))
        }
    }

    private inline fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
