package com.babytracker.sharing.domain.model

import java.time.Instant

data class ShareSnapshot(
    val lastSyncAt: Instant,
    val baby: BabySnapshot,
    val sessions: List<SessionSnapshot>,
    val sleepRecords: List<SleepSnapshot>,
    val bottleFeeds: List<BottleFeedSnapshot> = emptyList(),
    val inventoryTotalMl: Int? = null,
    val inventoryBagCount: Int? = null,
    val inventoryUpdatedAt: Long? = null,
    val milkBags: List<MilkBagSnapshot> = emptyList(),
    val sleepPrediction: SleepPredictionSnapshot? = null,
    val growth: List<GrowthSnapshot> = emptyList(),
    val milestones: List<MilestoneSnapshot> = emptyList(),
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

data class BottleFeedSnapshot(
    val timestamp: Long,
    val volumeMl: Int,
    val type: String,
    val clientId: String = "",
    val author: String = "OWNER",
    val notes: String? = null,
)

data class MilkBagSnapshot(
    val id: Long,
    val collectionDateMs: Long,
    val volumeMl: Int,
    val notes: String?,
)

data class GrowthSnapshot(
    val type: String,
    val takenAtMs: Long,
    val valueCanonical: Long,
    val notes: String? = null,
)

// Photos are intentionally omitted — milestone photos stay on-device and are never synced.
data class MilestoneSnapshot(
    val title: String,
    val dateEpochDay: Long,
    val timeMinuteOfDay: Int? = null,
    val note: String? = null,
)
