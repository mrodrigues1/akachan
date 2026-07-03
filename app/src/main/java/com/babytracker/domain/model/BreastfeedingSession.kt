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

    /**
     * Which breast to offer next, or null while the session is in progress: the side that was used
     * less last time. Pause time is subtracted via [sideDurationsUntil], but attribution is
     * per-session, not per-side — see the ceiling comment there for how that can skew the pick.
     */
    fun recommendedNextSide(): BreastSide? {
        val end = endTime ?: return null
        val (firstSideDuration, secondSideDuration) = sideDurationsUntil(end)
        val oppositeSide = if (startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
        return when {
            // No switch: only the starting side was used — recommend the other side
            secondSideDuration == null -> oppositeSide
            // Second side was used less than first — recommend second side (opposite of starting)
            secondSideDuration < firstSideDuration -> oppositeSide
            // First side was used less (or both equal) — recommend first/starting side
            else -> startingSide
        }
    }

    // ponytail: pausedDurationMs is session-wide, so the whole pause is charged to the second
    // side (or the only side) — a pause taken before the switch over-counts the first side and
    // under-counts the second, skewing recommendedNextSide(). Split into firstSide/secondSide
    // paused ms folded at resume based on switchTime if pause-before-switch becomes a real pattern.
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
