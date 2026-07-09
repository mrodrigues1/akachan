package com.babytracker.domain.model

import java.time.Duration
import java.time.Instant

data class SleepRecord(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val sleepType: SleepType,
    val notes: String? = null,
    val timezoneId: String? = null,
    // Stable cross-device identity (UUID). Minted on insert; preserved across edits. Default blank
    // so edit/preview call sites compile — the repository mints a real UUID for blank inserts.
    val clientId: String = "",
    val startedBy: SleepAuthor = SleepAuthor.OWNER,
) {
    init {
        require(endTime == null || endTime.isAfter(startTime)) { "endTime must be after startTime" }
    }

    val duration: Duration?
        get() = endTime?.let { Duration.between(startTime, it) }

    val isInProgress: Boolean
        get() = endTime == null
}
