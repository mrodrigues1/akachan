package com.babytracker.domain.sleep.feature

/**
 * Wake-interval distribution for one sleep type (nap or bedtime). Replaces four parallel nullable
 * fields (`*WakeIntervalCount`/`*WakeP25Millis`/`*WakeP50Millis`/`*WakeP75Millis`) previously carried
 * directly on [SleepMetrics] once per type.
 *
 * [p50Millis] can be set from a single interval (see SleepFeatureExtractor's median fallback), while
 * [p25Millis]/[p75Millis] require at least 4 samples — so [p50Millis] non-null with [p25Millis]/
 * [p75Millis] null is an expected state, not corruption. [iqrMillis] is derived rather than stored so
 * it can never disagree with [p25Millis]/[p75Millis].
 */
data class WakeIntervalStats(
    val count: Int = 0,
    val p25Millis: Long? = null,
    val p50Millis: Long? = null,
    val p75Millis: Long? = null,
) {
    val iqrMillis: Long? get() = if (p25Millis != null && p75Millis != null) p75Millis - p25Millis else null
}
