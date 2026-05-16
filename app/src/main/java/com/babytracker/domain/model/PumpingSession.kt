package com.babytracker.domain.model

import java.time.Duration
import java.time.Instant

data class PumpingSession(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val breast: PumpingBreast,
    val volumeMl: Int? = null,
    val notes: String? = null,
    val pausedAt: Instant? = null,
    val pausedDurationMs: Long = 0,
) {
    val isInProgress: Boolean get() = endTime == null
    val isPaused: Boolean get() = pausedAt != null
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it).minusMillis(pausedDurationMs).coerceAtLeast(Duration.ZERO) }

    fun activeDurationUntil(until: Instant): Duration =
        Duration.between(startTime, endTime ?: until)
            .minusMillis(pausedDurationMs)
            .coerceAtLeast(Duration.ZERO)
}
