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
    // Tolerance around the sleep window inside which a predicted next feed counts as "feed due".
    const val FEED_DUE_TOLERANCE_MINUTES = 30L
    // Type-specific intervals at which qualityC saturates to 1.0. Lowered 14 → 10 (Phase 6, AKA-154)
    // so logged data reaches full weight sooner — bedtime (~1 interval/day) personalizes in ~10 days
    // instead of two weeks. Eval-swept; {8,10,12} were equivalent on the data-rich cohorts, 10 keeps
    // more warm-up regularization than 8.
    const val FULL_PERSONALIZATION_INTERVALS = 10

    // Cap on how much the baby's own P50 displaces the age prior in the wake-target blend.
    // At full personalization (qualityC = 1) the baby's history gets at most this weight (90%)
    // vs the age prior (10%) — a deliberate shrinkage regularizer that still guards data-rich babies
    // against overfitting to their own noisy wake history. Used twice in the blend, hence named.
    // Raised 0.6 → 0.9 (Phase 6, AKA-154): the 60% cap predicted shorter-wake-window babies too late;
    // the eval sweep minimized short-wake MAE at 0.9 with no regression on typical/long-wake cohorts.
    const val MAX_PERSONALIZATION_WEIGHT = 0.9

    // Confidence-decay floor for the heuristic factors (Phase 6, AKA-155). The summed factor shift is
    // scaled by factorWeight(c) = FACTOR_FLOOR + (1 - FACTOR_FLOOR) * (1 - qualityC): full strength
    // when the baby is unknown (c = 0), fading toward FACTOR_FLOOR once the baby's own logged pattern
    // dominates the blend (c = 1). The population heuristics (circadian / sleep-debt / nap-budget) are
    // most valuable before there is history; once a baby is well known their own median already encodes
    // that structure, so the factors must carry less weight. FACTOR_FLOOR > 0 keeps a genuine
    // sleep-debt / nap-deficit day still nudging a known baby. Eval-swept against the factor cohorts.
    const val FACTOR_FLOOR = 0.6

    const val MIN_TYPE_INTERVALS = 3           // min type-specific intervals to use type P50
    const val OVERDUE_GRACE_MINUTES = 45L
    const val CUE_LED_MAX_AGE_WEEKS = 6
    const val EVAL_MIN_ANCHORS = 20
    const val EVAL_SCORE_HALF_WINDOW_MINUTES = MIN_HALF_WINDOW_MINUTES  // eval-harness score threshold only
    const val EVAL_MIN_SCORED = 20
    const val EVAL_MIN_MAE_GAIN_MIN = 5
    const val EVAL_MAX_REGRESSION = 0
    const val EVAL_ADVERSE_COHORT_MISSED_RATE_CAP = 0.20  // max missed-window rate regression for adverse personalized cohort
    const val CIRCADIAN_MIN_AGE_WEEKS = 6
    const val CIRCADIAN_FULL_WEIGHT_AGE_WEEKS = 12
    const val CIRCADIAN_MAX_SHIFT_MINUTES = 20L
    // Asymmetric cap (Phase 6, AKA-156). The reported symptom is one-directional — windows land too
    // far *ahead* — and a population heuristic that pushes a baby's bedtime *later* toward the textbook
    // slot directly works against that. So the circadian factor may pull earlier up to
    // CIRCADIAN_MAX_SHIFT_MINUTES but later only up to this much; eval-tuned to clear the residual
    // late bias on the typical/short-wake cohorts without regressing long-wake.
    const val CIRCADIAN_MAX_LATER_SHIFT_MINUTES = 5L
    const val CIRCADIAN_TARGET_NEUTRALITY_MINUTES = 10L
    const val SLEEP_DEBT_MAX_SHIFT_MINUTES = 20L
    const val SLEEP_DEBT_SCALE_MINUTES_PER_HOUR = 5L
    const val SLEEP_DEBT_MIN_HOURS = 1L
    const val NAP_BUDGET_MAX_SHIFT_MINUTES = 20L
    const val NAP_BUDGET_MINUTES_PER_NAP = 10L
    // Ceiling on the summed factor shift; below MAX_HALF_WINDOW_MINUTES. Deliberately equal to
    // OVERDUE_GRACE_MINUTES: a maximal later shift can at most consume the overdue grace period,
    // never revive a window that is already past grace.
    const val MAX_TOTAL_FACTOR_SHIFT_MINUTES = 45L
    const val MIN_QUALIFIED_TZ_PROVENANCE_RATE = 0.5f
    const val HIGH_CONFIDENCE_QUALITY_C_THRESHOLD = 0.8f
    const val DISRUPTION_LOOKBACK_HOURS = 48L
    const val ALGORITHM_VERSION = "sleep-pred-phase6-personalization-rebalance-1"
}
