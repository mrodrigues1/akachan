package com.babytracker.sharing.domain.model

data class SleepPredictionSnapshot(
    val stateLabel: String,
    val windowStart: Long? = null,
    val windowEnd: Long? = null,
    val bestEstimate: Long? = null,
    val confidence: String? = null,
    val reasons: List<String> = emptyList(),
    val feedPrompt: String? = null,
    val generatedAt: Long,
)
