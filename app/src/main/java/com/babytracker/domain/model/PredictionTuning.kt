package com.babytracker.domain.model

object PredictionTuning {
    const val LOOKBACK_LIMIT: Int = 20
    const val FRESHNESS_HORIZON_HOURS: Long = 12
    const val INTERVAL_MAX_MINUTES: Int = 360
    const val SAMPLE_SIZE_TARGET: Int = 5
    const val SAMPLE_SIZE_MIN: Int = 3
    const val OVERDUE_GRACE_MINUTES: Long = 90
}
