package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepMetrics
import java.time.Duration

data class SleepPredictionFactor(
    val adjustment: Duration,
    val reason: String? = null,
    val isEnabled: Boolean = true,
) {
    companion object {
        val Disabled = SleepPredictionFactor(Duration.ZERO, isEnabled = false)
        val Neutral = SleepPredictionFactor(Duration.ZERO)
    }
}

typealias CircadianFactorProvider = (
    Int,
    SleepType,
    Int?,
    Int?,
    Int,
) -> SleepPredictionFactor

typealias TimeOfDayFactorProvider = (
    SleepMetrics,
    SleepType,
    Int?,
    Boolean,
) -> SleepPredictionFactor

typealias SleepDebtFactorProvider = (
    Long,  // sleepLast24hMillis
    Long?, // avgDailySleepMillis (null if insufficient history)
    Int,   // ageInWeeks
) -> SleepPredictionFactor
