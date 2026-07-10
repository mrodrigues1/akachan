package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepType
import java.time.Duration

data class SleepPredictionFactor(
    val adjustment: Duration,
    val reason: SleepReason? = null,
) {
    companion object {
        val Neutral = SleepPredictionFactor(Duration.ZERO)
    }
}

// fun interfaces (not type aliases) so every call site is forced through named parameters where it
// matters: CircadianFactorProvider's two trailing Int? params were previously distinguishable only
// by a positional-tuple comment, and swapping them at a call site compiled cleanly while silently
// corrupting the circadian shift direction.

fun interface CircadianFactorProvider {
    operator fun invoke(
        ageInWeeks: Int,
        nextType: SleepType,
        currentMinuteOfDay: Int?,
        candidateMinuteOfDay: Int?,
    ): SleepPredictionFactor
}

fun interface SleepDebtFactorProvider {
    operator fun invoke(
        sleepLast24hMillis: Long,
        avgDailySleepMillis: Long?, // null if insufficient history
        ageInWeeks: Int,
    ): SleepPredictionFactor
}

fun interface NapBudgetFactorProvider {
    operator fun invoke(
        napCountToday: Int,
        ageInWeeks: Int,
        nextType: SleepType,
    ): SleepPredictionFactor
}
