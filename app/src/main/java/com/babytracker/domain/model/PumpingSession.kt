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
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it) }

    val isInProgress: Boolean
        get() = endTime == null

    val isPaused: Boolean
        get() = pausedAt != null

    val activeDuration: Duration?
        get() = endTime?.let { activeDurationUntil(it) }

    fun activeDurationUntil(until: Instant): Duration {
        val currentPausedMs = if (endTime == null && pausedAt != null) {
            Duration.between(pausedAt, until).toMillis().coerceAtLeast(0L)
        } else {
            0L
        }
        return Duration.between(startTime, endTime ?: until)
            .minusMillis(pausedDurationMs + currentPausedMs)
            .coerceAtLeast(Duration.ZERO)
    }
}
