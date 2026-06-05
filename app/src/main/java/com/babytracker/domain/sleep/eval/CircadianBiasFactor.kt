package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToLong

object CircadianBiasFactor {

    fun adjustment(
        ageInWeeks: Int,
        nextType: SleepType,
        currentMinuteOfDay: Int?,
        candidateMinuteOfDay: Int?,
        napCountToday: Int,
    ): SleepPredictionFactor {
        if (ageInWeeks < SleepPredictionTuning.CIRCADIAN_MIN_AGE_WEEKS) {
            return SleepPredictionFactor.Disabled
        }
        if (currentMinuteOfDay == null || candidateMinuteOfDay == null) return SleepPredictionFactor.Neutral
        if (nextType == SleepType.NAP || napCountToday < 0) return SleepPredictionFactor.Neutral

        val targetMinute = bedtimeMidpointMinute(ageInWeeks)
        val diffMinutes = shortestSignedMinuteDiff(from = candidateMinuteOfDay, to = targetMinute)
        if (abs(diffMinutes) <= SleepPredictionTuning.CIRCADIAN_TARGET_NEUTRALITY_MINUTES) {
            return SleepPredictionFactor.Neutral
        }

        val ramp = rampWeight(ageInWeeks)
        val capped = diffMinutes.coerceIn(
            -SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES.toInt(),
            SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES.toInt(),
        )
        val adjustmentMinutes = (capped * ramp).roundToLong()
        if (adjustmentMinutes == 0L) return SleepPredictionFactor.Neutral

        return SleepPredictionFactor(
            adjustment = Duration.ofMinutes(adjustmentMinutes),
            reason = "Adjusted toward the expected bedtime circadian slot",
        )
    }

    private fun rampWeight(ageInWeeks: Int): Double {
        val minAge = SleepPredictionTuning.CIRCADIAN_MIN_AGE_WEEKS
        val fullAge = SleepPredictionTuning.CIRCADIAN_FULL_WEIGHT_AGE_WEEKS
        return ((ageInWeeks - minAge).toDouble() / (fullAge - minAge).toDouble()).coerceIn(0.0, 1.0)
    }

    private fun bedtimeMidpointMinute(ageInWeeks: Int): Int {
        val window = SleepAgePriors.getBedtimeWindow(ageInWeeks)
        return circularAverage(minuteOfDay(window.start), minuteOfDay(window.endInclusive))
    }

    private fun minuteOfDay(time: LocalTime): Int = time.hour * MINUTES_PER_HOUR + time.minute

    private fun shortestSignedMinuteDiff(from: Int, to: Int): Int {
        val raw = (to - from + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return if (raw > MINUTES_PER_DAY / 2) raw - MINUTES_PER_DAY else raw
    }

    private fun circularAverage(a: Int, b: Int): Int {
        val diff = shortestSignedMinuteDiff(from = a, to = b)
        return (a + diff / 2 + MINUTES_PER_DAY) % MINUTES_PER_DAY
    }

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 1_440
}
