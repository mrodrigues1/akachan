package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType
import java.time.Duration

data class SleepPredictionFactor(
    val adjustment: Duration,
    val reason: String? = null,
) {
    companion object {
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

typealias SleepDebtFactorProvider = (
    Long,  // sleepLast24hMillis
    Long?, // avgDailySleepMillis (null if insufficient history)
    Int,   // ageInWeeks
) -> SleepPredictionFactor

typealias NapBudgetFactorProvider = (
    Int,       // napCountToday
    Int,       // ageInWeeks
    com.babytracker.domain.model.SleepType, // nextType
) -> SleepPredictionFactor
