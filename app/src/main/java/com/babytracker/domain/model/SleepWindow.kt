package com.babytracker.domain.model

import java.time.Instant

data class SleepWindow(
    val windowStart: Instant,
    val windowEnd: Instant,
    val bestEstimate: Instant,
    // The predictor's resolved NAP/NIGHT_SLEEP decision; consumers must not re-derive it (AKACHAN-305).
    val sleepType: SleepType,
    val confidence: Confidence,
    val reasons: List<SleepReason>,
    val feedDue: Boolean,
)
