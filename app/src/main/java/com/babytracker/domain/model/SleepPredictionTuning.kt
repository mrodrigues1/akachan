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

    const val MIN_HALF_WINDOW_MINUTES = 15L    // floor for dynamic window
    const val MAX_HALF_WINDOW_MINUTES = 60L    // ceiling for dynamic window
    const val HALF_WINDOW_MINUTES = MIN_HALF_WINDOW_MINUTES  // kept for eval harness score threshold
    const val FULL_PERSONALIZATION_INTERVALS = 14

    // Cap on how much the baby's own P50 displaces the age prior in the wake-target blend.
    // At full personalization (qualityC = 1) the baby's history gets at most this weight (60%)
    // vs the age prior (40%) — a deliberate shrinkage regularizer that guards data-rich babies
    // against overfitting to their own noisy wake history. Used twice in the blend, hence named.
    const val MAX_PERSONALIZATION_WEIGHT = 0.6
    const val MIN_TYPE_INTERVALS = 3           // min type-specific intervals to use type P50
    const val OVERDUE_GRACE_MINUTES = 45L
    const val CUE_LED_MAX_AGE_WEEKS = 6
    const val EVAL_MIN_ANCHORS = 20
    const val EVAL_MIN_SCORED = 20
    const val EVAL_MIN_MAE_GAIN_MIN = 5
    const val EVAL_MAX_REGRESSION = 0
    const val EVAL_ADVERSE_COHORT_MISSED_RATE_CAP = 0.20  // max missed-window rate regression for adverse personalized cohort
    const val CIRCADIAN_MIN_AGE_WEEKS = 6
    const val CIRCADIAN_FULL_WEIGHT_AGE_WEEKS = 12
    const val CIRCADIAN_MAX_SHIFT_MINUTES = 20L
    const val CIRCADIAN_TARGET_NEUTRALITY_MINUTES = 10L
    const val SLEEP_DEBT_MAX_SHIFT_MINUTES = 20L
    const val SLEEP_DEBT_SCALE_MINUTES_PER_HOUR = 5L
    const val SLEEP_DEBT_MIN_HOURS = 1L
    const val NAP_BUDGET_MAX_SHIFT_MINUTES = 20L
    const val NAP_BUDGET_MINUTES_PER_NAP = 10L
    const val MAX_TOTAL_FACTOR_SHIFT_MINUTES = 45L  // ceiling on summed factor shift; below MAX_HALF_WINDOW_MINUTES
    const val MIN_QUALIFIED_TZ_PROVENANCE_RATE = 0.5f
    const val HIGH_CONFIDENCE_QUALITY_C_THRESHOLD = 0.8f
    const val DISRUPTION_LOOKBACK_HOURS = 48L
    const val ALGORITHM_VERSION = "sleep-pred-phase4-iqr-fallback-1"
}
