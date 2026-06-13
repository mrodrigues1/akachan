package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class CircadianBiasFactorTest {

    @Test
    fun `disabled below cue-led age`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS - 1,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 18 * 60,
            candidateMinuteOfDay = 17 * 60,
            napCountToday = 0,
        )

        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `night sleep shifts toward bedtime midpoint and is capped`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = 20,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 18 * 60,
            candidateMinuteOfDay = 16 * 60,
            napCountToday = 2,
        )

        assertTrue(factor.adjustment > Duration.ZERO)
        assertTrue(factor.adjustment <= Duration.ofMinutes(SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES))
        assertTrue(factor.reason!!.contains("circadian", ignoreCase = true))
    }

    @Test
    fun `night sleep shift is negative when candidate is later than bedtime target`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = 20,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 22 * 60,
            candidateMinuteOfDay = 23 * 60,
            napCountToday = 2,
        )

        assertTrue(factor.adjustment < Duration.ZERO)
    }

    @Test
    fun `near target returns neutral`() {
        // 20w bedtime window 19:00-20:00, midpoint 19:30 (1170 min). Candidate at 19:25 is 5 min away — within
        // CIRCADIAN_TARGET_NEUTRALITY_MINUTES threshold of 10, so adjustment is suppressed.
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = 20,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 19 * 60,
            candidateMinuteOfDay = 19 * 60 + 25,
            napCountToday = 2,
        )

        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `ramp weight is smaller at eight weeks than twelve weeks`() {
        val eightWeeks = CircadianBiasFactor.adjustment(
            ageInWeeks = 8,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 18 * 60,
            candidateMinuteOfDay = 16 * 60,
            napCountToday = 2,
        )
        val twelveWeeks = CircadianBiasFactor.adjustment(
            ageInWeeks = 12,
            nextType = SleepType.NIGHT_SLEEP,
            currentMinuteOfDay = 18 * 60,
            candidateMinuteOfDay = 16 * 60,
            napCountToday = 2,
        )

        assertTrue(eightWeeks.adjustment < twelveWeeks.adjustment)
    }

    @Test
    fun `nap circadian bias is neutral until a real current-day anchor exists`() {
        val factor = CircadianBiasFactor.adjustment(
            ageInWeeks = 20,
            nextType = SleepType.NAP,
            currentMinuteOfDay = 10 * 60,
            candidateMinuteOfDay = 12 * 60,
            napCountToday = 0,
        )

        assertEquals(SleepPredictionFactor.Neutral, factor)
    }
}
