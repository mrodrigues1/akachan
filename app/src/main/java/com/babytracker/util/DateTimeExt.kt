package com.babytracker.util

import android.content.Context
import com.babytracker.R
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Caches parsed [DateTimeFormatter]s so hot paths (per-row PDF/history formatting) don't re-parse the
 * same pattern on every call — pattern parsing is the expensive part of `ofPattern`. Keyed by both the
 * pattern and the current FORMAT locale so a runtime language switch (en ↔ pt-BR) still picks the right
 * month/AM-PM text instead of freezing it. The zone is applied per call by the callers, matching the
 * previous `withZone(systemDefault())` behavior exactly.
 */
private val patternFormatters = ConcurrentHashMap<String, DateTimeFormatter>()

private fun patternFormatter(pattern: String): DateTimeFormatter {
    val locale = Locale.getDefault(Locale.Category.FORMAT)
    return patternFormatters.getOrPut("$pattern|$locale") {
        DateTimeFormatter.ofPattern(pattern, locale)
    }
}

fun Instant.formatTime(): String =
    patternFormatter("HH:mm").format(this.atZone(ZoneId.systemDefault()))

fun Instant.formatDateTime(): String =
    patternFormatter("MMM dd, HH:mm").format(this.atZone(ZoneId.systemDefault()))

fun Instant.formatPdfDateTime(): String =
    patternFormatter("MMM dd yyyy, HH:mm").format(this.atZone(ZoneId.systemDefault()))

fun Instant.formatTime12h(): String =
    patternFormatter("hh:mm a").format(this.atZone(ZoneId.systemDefault()))

/** 12-hour clock time without a leading zero on the hour ("9:05 AM"). Used by notification copy. */
fun Instant.formatClockTime12h(): String =
    patternFormatter("h:mm a").format(this.atZone(ZoneId.systemDefault()))

/**
 * Duration between two wall-clock times on [onDate], treating an end-before-start pair as crossing
 * midnight (the start shifts to the previous day). Returns null when the result is zero or negative.
 * Used for live entry-duration previews.
 */
fun durationBetween(start: LocalTime, end: LocalTime, onDate: LocalDate): Duration? {
    val zone = ZoneId.systemDefault()
    var startInstant = start.atDate(onDate).atZone(zone).toInstant()
    val endInstant = end.atDate(onDate).atZone(zone).toInstant()
    if (startInstant > endInstant) {
        startInstant = start.atDate(onDate.minusDays(1)).atZone(zone).toInstant()
    }
    val d = Duration.between(startInstant, endInstant)
    return if (d.isNegative || d.isZero) null else d
}

/**
 * Full, locale-aware date. The locale, not a fixed pattern, decides the word order and
 * separators: "Saturday, June 20, 2026" (en) vs "sábado, 20 de junho de 2026" (pt-BR).
 */
fun LocalDate.formatLongDate(): String =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).format(this)

/**
 * Short, locale-aware time honoring the locale's clock convention:
 * "2:30 PM" (en, 12-hour) vs "14:30" (pt-BR, 24-hour).
 */
fun LocalTime.formatShortTime(): String =
    DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).format(this)

fun Duration.formatDuration(): String {
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    val seconds = (seconds % 60).toInt()
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}

fun LocalDate.toRelativeLabel(today: String, yesterday: String): String {
    val now = LocalDate.now()
    return when (this) {
        now -> today
        now.minusDays(1) -> yesterday
        else -> patternFormatter("MMM d").format(this)
    }
}

fun Duration.formatElapsedAgo(context: Context): String {
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    return when {
        hours > 0 -> context.getString(R.string.elapsed_hours_minutes_ago, hours, minutes)
        minutes > 0 -> context.getString(R.string.elapsed_minutes_ago, minutes)
        else -> context.getString(R.string.elapsed_just_now)
    }
}

fun Long.formatMinutesSeconds(): String {
    val totalMinutes = this / 60
    val seconds = this % 60
    return String.format(Locale.US, "%02d:%02d", totalMinutes, seconds)
}

fun Duration.formatElapsedShort(): String {
    if (isNegative || isZero) return "0m"
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    return if (hours > 0) "${hours}h ${"%02d".format(minutes)}m" else "${minutes}m"
}

fun Duration.formatElapsedCompact(context: Context): String {
    val days = toDays()
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    return when {
        days > 0 -> "${days}d"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> context.getString(R.string.elapsed_just_now)
    }
}

/**
 * Group items by the local calendar day (in [zone]) of [instantOf], newest day first, with each
 * day's items sorted newest-first too. Shared by the history screens that show a reverse-chronological
 * day-sectioned list.
 */
fun <T> List<T>.groupByDateDescending(
    zone: ZoneId = ZoneId.systemDefault(),
    instantOf: (T) -> Instant,
): List<Pair<LocalDate, List<T>>> =
    groupBy { instantOf(it).atZone(zone).toLocalDate() }
        .toSortedMap(reverseOrder())
        .map { (date, items) -> date to items.sortedByDescending { instantOf(it) } }
