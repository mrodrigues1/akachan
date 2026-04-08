package com.babytracker.util

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
