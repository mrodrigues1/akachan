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

    val activeDuration: Duration?
        get() = endTime?.let { activeDurationUntil(it) }

    fun activeDurationUntil(until: Instant): Duration =
        Duration.between(startTime, endTime ?: until)
            .minus(Duration.ofMillis(pausedDurationMs))
            .coerceAtLeast(Duration.ZERO)

    fun sideDurationsUntil(until: Instant): Pair<Duration, Duration?> {
        val effectiveUntil = endTime ?: until
        val pausedDuration = Duration.ofMillis(pausedDurationMs)
        val firstSideDuration = switchTime
            ?.let { Duration.between(startTime, it) }
            ?: Duration.between(startTime, effectiveUntil).minus(pausedDuration)
        val secondSideDuration = switchTime
            ?.let { Duration.between(it, effectiveUntil).minus(pausedDuration) }

        return firstSideDuration.coerceAtLeast(Duration.ZERO) to
            secondSideDuration?.coerceAtLeast(Duration.ZERO)
    }
}
