package com.babytracker.util

import java.time.Duration
import java.time.Instant
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

fun Duration.formatDuration(): String {
    val hours = toHours()
    val minutes = (toMinutes() % 60).toInt()
    val seconds = (getSeconds() % 60).toInt()
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m ${seconds}s"
        else -> "${seconds}s"
    }
}
