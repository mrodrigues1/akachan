package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.SleepReason

/**
 * Synced sleep-window prediction. Deliberately semantic: [reasons] and [feedDue] carry the
 * locale-agnostic facts, and each device resolves them to text in its own language at the UI
 * edge — the payload never contains presentation strings.
 */
data class SleepPredictionSnapshot(
    val stateLabel: String,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
    val bestEstimate: Long? = null,
    val confidence: String? = null,
    val reasons: List<SleepReason> = emptyList(),
    val feedDue: Boolean = false,
    val generatedAt: Long,
)
