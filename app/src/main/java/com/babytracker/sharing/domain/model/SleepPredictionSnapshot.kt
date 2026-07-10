package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.SleepReason

/**
 * Synced sleep-window prediction. Deliberately semantic: [reasons] and [feedDue] carry the
 * locale-agnostic facts, and each device resolves them to text in its own language at the UI
 * edge — the payload never contains presentation strings.
 */
data class SleepPredictionSnapshot(
    val stateLabel: PredictionStateLabel,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
    val bestEstimate: Long? = null,
    val confidence: String? = null,
    val reasons: List<SleepReason> = emptyList(),
    val feedDue: Boolean = false,
    val generatedAt: Long,
)

/**
 * The [SleepPredictionSnapshot.stateLabel] wire value. Names match the strings the producer
 * ([toSnapshot]) has always emitted, serialized via `.name` — the wire bytes are unchanged.
 * [UNAVAILABLE] is never produced locally; it is the read-side fallback for a wire value the
 * current build doesn't recognize (missing field, older/newer app version).
 */
enum class PredictionStateLabel {
    WINDOW,
    NEED_MORE_DATA,
    CUE_LED,
    CURRENTLY_SLEEPING,
    AFTER_ACTIVE_FEED,
    OVERDUE,
    UNAVAILABLE,
}
