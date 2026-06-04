package com.babytracker.domain.model

object SleepPredictionTuning {
    const val MAX_NAP_DURATION_HOURS = 4L
    const val MAX_NIGHT_SLEEP_DURATION_HOURS = 18L
    const val MAX_OPEN_SLEEP_AGE_HOURS = 18L
    const val MAX_FEED_DURATION_HOURS = 4L
    const val MAX_OPEN_FEED_AGE_HOURS = 4L
    const val LOOKBACK_DAYS = 14L
    const val FRESHNESS_HORIZON_HOURS = 12L
    const val MIN_COMPLETED_INTERVALS = 5
    const val MIN_LOCAL_DAYS = 3
    const val MIN_PLAUSIBLE_WAKE_INTERVAL_MINUTES = 15L
    const val MAX_PLAUSIBLE_WAKE_INTERVAL_HOURS = 6L
    const val INSTABILITY_CEILING_MINUTES = 45L
    const val MAX_INVALID_RATE = 0.25f

    const val HALF_WINDOW_MINUTES = 15L
    const val FULL_PERSONALIZATION_INTERVALS = 14
    const val OVERDUE_GRACE_MINUTES = 45L
    const val CUE_LED_MAX_AGE_WEEKS = 6
    const val CANDIDATE_STEP_MINUTES = 5L
    const val SHRINK_N = 10
    const val MAX_BIAS_MINUTES = 15L
    const val EVAL_MIN_ANCHORS = 20
    const val EVAL_MIN_SCORED = 5
    const val EVAL_MIN_MAE_GAIN_MIN = 5 // TODO(AKA-91): Phase 2 factor gate — enforce in comparison harness
    const val EVAL_MAX_REGRESSION = 0 // TODO(AKA-91): Phase 2 factor gate — no regression allowed vs baseline
    const val ALGORITHM_VERSION = "sleep-pred-baseline-1"
}
