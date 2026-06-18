package com.babytracker.domain.usecase.breastfeeding

import java.time.Duration
import java.time.Instant

/**
 * Returns `null` when the proposed edit is valid, otherwise the reason it is invalid.
 * Order is intentional: future-time errors take priority over ordering errors so the user
 * sees the most actionable correction first.
 */
fun validateBreastfeedingEdit(
    startTime: Instant,
    endTime: Instant?,
    pausedDurationMs: Long,
    now: Instant,
): BreastfeedingEditError? {
    if (startTime.isAfter(now)) return BreastfeedingEditError.START_IN_FUTURE
    if (endTime != null && endTime.isAfter(now)) return BreastfeedingEditError.END_IN_FUTURE
    if (endTime != null && !endTime.isAfter(startTime)) return BreastfeedingEditError.END_BEFORE_START
    if (endTime != null) {
        val sessionMs = Duration.between(startTime, endTime).toMillis()
        if (pausedDurationMs > sessionMs) return BreastfeedingEditError.SESSION_SHORTER_THAN_PAUSES
    }
    return null
}
