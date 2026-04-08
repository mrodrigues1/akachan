package com.babytracker.domain.model

import java.time.Duration
import java.time.Instant

data class SleepRecord(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val sleepType: SleepType,
    val notes: String? = null
) {
    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it) }

    val isInProgress: Boolean
        get() = endTime == null
}
