package com.babytracker.domain.sleep.feature

data class SleepFeatures(
    val validIntervals: List<SleepInterval>,
    val feedIntervals: List<BreastfeedInterval>,
    val metrics: SleepMetrics,
    val quality: EvidenceQuality,
    val currentMinuteOfDay: Int? = null,
    val hasActiveDisruption: Boolean = false,
)
