package com.babytracker.domain.model

data class TodayFeedingSummary(
    val bottleVolumeMl: Int = 0,
    val bottleCount: Int = 0,
    val breastfeedingCount: Int = 0,
) {
    val totalFeedCount: Int get() = bottleCount + breastfeedingCount
    val hasAny: Boolean get() = totalFeedCount > 0
}
