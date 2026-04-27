package com.babytracker.sharing.domain.model

import java.time.Instant

data class ShareSnapshot(
    val lastSyncAt: Instant,
    val baby: BabySnapshot,
    val sessions: List<SessionSnapshot>,
    val sleepRecords: List<SleepSnapshot>,
)

data class BabySnapshot(
    val name: String,
    val birthDateMs: Long,
    val allergies: List<String>,
)

data class SessionSnapshot(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val startingSide: String,
    val switchTime: Long?,
    val pausedDurationMs: Long,
    val notes: String?,
)

data class SleepSnapshot(
    val id: Long,
    val startTime: Long,
    val endTime: Long?,
    val sleepType: String,
    val notes: String?,
)
