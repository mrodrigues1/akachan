package com.babytracker.domain.model

import java.time.Duration
import java.time.Instant
import java.util.UUID

data class SleepRecord(
    val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant? = null,
    val sleepType: SleepType,
    val notes: String? = null,
    val timezoneId: String? = null,
    // Stable cross-device identity (UUID). Every freshly constructed record mints its own, matching
    // the SleepEntity default, so two separate records can never collide on the unique client_id
    // index and a blank clientId can now only mean a legacy (pre-SPEC-008) row read from Room. Edits
    // preserve the stored id (SleepDao.updateRecordPreservingIdentity), so the default never
    // overwrites it. Partner-authored records pass op.entryClientId explicitly.
    val clientId: String = UUID.randomUUID().toString(),
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
