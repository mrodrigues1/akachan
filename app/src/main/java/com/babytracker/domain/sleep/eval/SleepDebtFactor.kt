package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration

object SleepDebtFactor {

    fun adjustment(
        sleepLast24hMillis: Long,
        avgDailySleepMillis: Long?,
        ageInWeeks: Int,
    ): SleepPredictionFactor {
        val agePriorMidpointMillis = agePriorMidpointMillis(ageInWeeks)
        val personalTargetMillis = if (avgDailySleepMillis != null) {
            (agePriorMidpointMillis + avgDailySleepMillis) / 2
        } else {
            agePriorMidpointMillis
        }

        val debtMillis = personalTargetMillis - sleepLast24hMillis
        val debtHours = debtMillis.toDouble() / Duration.ofHours(1).toMillis().toDouble()

        if (kotlin.math.abs(debtHours) < SleepPredictionTuning.SLEEP_DEBT_MIN_HOURS) {
            return SleepPredictionFactor.Neutral
        }

        // Positive debt (under-slept) → shift earlier → negative adjustment
        val rawShiftMinutes = (debtHours * SleepPredictionTuning.SLEEP_DEBT_SCALE_MINUTES_PER_HOUR).toLong()
        val clampedShiftMinutes = rawShiftMinutes.coerceIn(
            -SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES,
            SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES,
        )
        if (clampedShiftMinutes == 0L) return SleepPredictionFactor.Neutral

        val direction = if (clampedShiftMinutes > 0) "earlier" else "later"
        return SleepPredictionFactor(
            adjustment = Duration.ofMinutes(-clampedShiftMinutes),
            reason = "Sleep debt: 24h total vs. daily target suggests $direction window",
        )
    }

    private fun agePriorMidpointMillis(ageInWeeks: Int): Long {
        val range = SleepAgePriors.getTotalSleepRecommendation(ageInWeeks)
        return (range.start.toMillis() + range.endInclusive.toMillis()) / 2L
    }
}
