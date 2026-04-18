package com.babytracker.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun Instant.formatTime(): String =
    DateTimeFormatter.ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

fun Instant.formatDateTime(): String =
    DateTimeFormatter.ofPattern("MMM dd, HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(this)

fun Instant.formatTime12h(): String =
    DateTimeFormatter.ofPattern("hh:mm a")
        .withZone(ZoneId.systemDefault())
        .format(this)

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

fun LocalDate.toRelativeLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> DateTimeFormatter.ofPattern("MMM d").format(this)
    }
}

fun Duration.formatElapsedAgo(): String {
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    return when {
        hours > 0 -> "${hours}h ${minutes}m ago"
        minutes > 0 -> "${minutes}m ago"
        else -> "Just now"
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
