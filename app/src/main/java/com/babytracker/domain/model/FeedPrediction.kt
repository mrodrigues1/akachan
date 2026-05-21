package com.babytracker.domain.model

import java.time.Instant

data class FeedPrediction(
    val predictedAt: Instant,
    val averageIntervalMinutes: Int,
    val sampleSize: Int,
    val isOverdue: Boolean,
    val minutesUntil: Int,
)
