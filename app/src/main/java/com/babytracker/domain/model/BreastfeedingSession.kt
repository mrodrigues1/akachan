package com.babytracker.domain.model

import java.time.Duration
import java.time.Instant

data class BreastfeedingSession(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val startingSide: BreastSide,
    val switchTime: Instant? = null,
    val notes: String? = null,
    val pausedAt: Instant? = null,
    val pausedDurationMs: Long = 0
) {
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it) }

    val isInProgress: Boolean
        get() = endTime == null

    val isPaused: Boolean
        get() = pausedAt != null
}
