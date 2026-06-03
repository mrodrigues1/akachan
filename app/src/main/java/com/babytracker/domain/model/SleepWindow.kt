package com.babytracker.domain.model

import java.time.Instant

data class SleepWindow(
    val windowStart: Instant,
    val windowEnd: Instant,
    val bestEstimate: Instant,
    val confidence: Confidence,
    val reasons: List<String>,
    val feedPrompt: String?,
    val safetyPrompt: String,
)
