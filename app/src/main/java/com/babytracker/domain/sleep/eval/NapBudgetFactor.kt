package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration

object NapBudgetFactor {

    fun adjustment(
        napCountToday: Int,
        ageInWeeks: Int,
        nextType: SleepType,
    ): SleepPredictionFactor {
        if (nextType != SleepType.NAP) return SleepPredictionFactor.Neutral

        val expectedNaps = SleepAgePriors.getScheduledNapCount(ageInWeeks)
        val deficit = expectedNaps - napCountToday

        if (deficit <= 0) return SleepPredictionFactor.Neutral

        val rawShiftMinutes = deficit.toLong() * SleepPredictionTuning.NAP_BUDGET_MINUTES_PER_NAP
        val clampedShiftMinutes = rawShiftMinutes.coerceAtMost(SleepPredictionTuning.NAP_BUDGET_MAX_SHIFT_MINUTES)

        return SleepPredictionFactor(
            adjustment = Duration.ofMinutes(-clampedShiftMinutes),
            reason = SleepReason.NapDeficit(deficit),
        )
    }
}
