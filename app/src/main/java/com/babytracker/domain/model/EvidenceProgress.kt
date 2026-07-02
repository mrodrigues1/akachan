package com.babytracker.domain.model

data class EvidenceProgress(
    val completedIntervals: Int,
    val requiredIntervals: Int,
    val localDays: Int,
    val requiredLocalDays: Int,
    val hint: EvidenceHint,
)

// Semantic hint resolved to localized copy at the UI (see util/SleepReasonText.kt), mirroring
// the SleepReason pattern from AKACHAN-302 (AKACHAN-307).
enum class EvidenceHint {
    NEED_MORE_INTERVALS,
    NEED_MORE_DAYS,
    NEED_FRESH_RECORD,
    PATTERN_SETTLING,
}
