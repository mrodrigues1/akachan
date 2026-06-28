package com.babytracker.sharing.domain.model

enum class SleepOpAction { START, STOP, UPDATE }

// Flat/nullable per action, mirroring FeedOp. One op doc per action; the primary applies and deletes.
// startTimeMs: START (tap) + UPDATE (edited). endTimeMs: STOP (tap) + UPDATE (null = still in progress).
// sleepType holds the SleepType.name ("NAP"/"NIGHT_SLEEP"); the serializer lowercases it for the wire.
data class SleepOp(
    override val opId: String,
    val action: SleepOpAction,
    val entryClientId: String,
    val authorUid: String,
    override val createdAtMs: Long,
    val startTimeMs: Long? = null,
    val endTimeMs: Long? = null,
    val sleepType: String? = null,
    val notes: String? = null,
) : PendingOp
