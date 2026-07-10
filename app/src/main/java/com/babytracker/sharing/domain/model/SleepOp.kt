package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.SleepType

enum class SleepOpAction { START, STOP, UPDATE }

// Flat/nullable per action, mirroring FeedOp. One op doc per action; the primary applies and deletes.
// startTimeMs: START (tap) + UPDATE (edited). endTimeMs: STOP (tap) + UPDATE (null = still in progress).
// sleepType is a typed SleepType; the Firestore serializer lowercases it to the wire ("nap"/"night_sleep").
data class SleepOp(
    override val opId: String,
    val action: SleepOpAction,
    val entryClientId: String,
    val authorUid: String,
    override val createdAtMs: Long,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val sleepType: SleepType? = null,
    val notes: String? = null,
) : PendingOp
