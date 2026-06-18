package com.babytracker.domain.usecase.pumping

import java.time.Instant

fun validatePumpingEdit(
    startTime: Instant,
    endTime: Instant?,
    volumeMl: Int?,
    pausedDurationMs: Long,
    now: Instant,
): PumpingEditError? {
    if (startTime.isAfter(now)) return PumpingEditError.START_IN_FUTURE
    if (endTime != null && !endTime.isAfter(startTime)) return PumpingEditError.END_BEFORE_START
    if (endTime != null && endTime.isAfter(now)) return PumpingEditError.END_IN_FUTURE
    if (endTime != null) {
        val active = endTime.toEpochMilli() - startTime.toEpochMilli() - pausedDurationMs
        if (active < 0) return PumpingEditError.PAUSE_EXCEEDS_SESSION
    }
    if (volumeMl != null && volumeMl <= 0) return PumpingEditError.VOLUME_NOT_POSITIVE
    return null
}
