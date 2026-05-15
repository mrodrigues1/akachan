package com.babytracker.domain.usecase.breastfeeding

import java.time.Duration
import java.time.Instant

/**
 * Returns `null` when the proposed edit is valid, otherwise a user-facing error message.
 * Order is intentional: future-time errors take priority over ordering errors so the user
 * sees the most actionable correction first.
 */
fun validateBreastfeedingEdit(
    startTime: Instant,
    endTime: Instant?,
    pausedDurationMs: Long,
    now: Instant,
): String? {
    if (startTime.isAfter(now)) return "Start time can't be in the future"
    if (endTime != null && endTime.isAfter(now)) return "End time can't be in the future"
    if (endTime != null && !endTime.isAfter(startTime)) return "End time must be after start time"
    if (endTime != null) {
        val sessionMs = Duration.between(startTime, endTime).toMillis()
        if (pausedDurationMs > sessionMs) return "Session is shorter than recorded pauses"
    }
    return null
}
