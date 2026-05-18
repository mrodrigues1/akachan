package com.babytracker.domain.usecase.pumping

import java.time.Instant

fun validatePumpingEdit(
    startTime: Instant,
    endTime: Instant?,
    volumeMl: Int?,
    pausedDurationMs: Long,
    now: Instant,
): String? {
    if (startTime.isAfter(now)) return "Start cannot be in the future"
    if (endTime != null && !endTime.isAfter(startTime)) return "End must be after start"
    if (endTime != null && endTime.isAfter(now)) return "End cannot be in the future"
    if (endTime != null) {
        val active = endTime.toEpochMilli() - startTime.toEpochMilli() - pausedDurationMs
        if (active < 0) return "Paused time exceeds session length"
    }
    if (volumeMl != null && volumeMl <= 0) return "Volume must be greater than 0"
    return null
}
