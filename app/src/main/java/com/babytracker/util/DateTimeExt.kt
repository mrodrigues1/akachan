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

fun Instant.formatTime(): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

fun Instant.formatDateTime(): String =
    DateTimeFormatter.ofPattern("MMM dd, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

fun Instant.formatPdfDateTime(): String =
    DateTimeFormatter.ofPattern("MMM dd yyyy, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

fun Instant.formatTime12h(): String =
    DateTimeFormatter.ofPattern("hh:mm a")
        .withZone(ZoneId.systemDefault())
        .format(this)

/** 12-hour clock time without a leading zero on the hour ("9:05 AM"). Used by notification copy. */
fun Instant.formatClockTime12h(): String =
    DateTimeFormatter.ofPattern("h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(this)

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

fun <T> List<T>.groupByLocalDate(keySelector: (T) -> Instant): Map<LocalDate, List<T>> =
    groupBy { keySelector(it).atZone(ZoneId.systemDefault()).toLocalDate() }

fun LocalDate.toRelativeLabel(today: String, yesterday: String): String {
    val now = LocalDate.now()
    return when (this) {
        now -> today
        now.minusDays(1) -> yesterday
        else -> DateTimeFormatter.ofPattern("MMM d").format(this)
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
