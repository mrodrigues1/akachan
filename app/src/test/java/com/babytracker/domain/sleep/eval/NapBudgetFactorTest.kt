package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class NapBudgetFactorTest {

    // ageInWeeks = 20 → getScheduledNapCount = getDefaultWakeWindows(20).size - 1
    // getDefaultWakeWindows(20): ageInWeeks in 16..23 → [105, 135, 150] → size=3 → expectedNaps=2
    private val ageInWeeks = 20

    @Test
    fun `neutral when nextType is NIGHT_SLEEP`() {
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 0,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NIGHT_SLEEP,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `neutral when deficit is zero (should not happen for NAP but is defensive)`() {
        // expectedNaps(20w) = 2; deficit = 2 - 2 = 0
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 2,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `deficit of 1 produces a negative adjustment (shift earlier)`() {
        // napCountToday = 1; expectedNaps = 2 → deficit = 1
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 1,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        assertTrue(
            factor.adjustment < Duration.ZERO,
            "Deficit of 1 nap should produce an earlier (negative) adjustment, got ${factor.adjustment}",
        )
        assertEquals(
            Duration.ofMinutes(SleepPredictionTuning.NAP_BUDGET_MINUTES_PER_NAP),
            factor.adjustment.abs(),
            "Deficit of 1 should produce NAP_BUDGET_MINUTES_PER_NAP shift",
        )
    }

    @Test
    fun `deficit of 2 produces twice the shift of deficit of 1, capped at max`() {
        // napCountToday = 0; expectedNaps = 2 → deficit = 2
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 0,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        val expectedShiftMinutes = (2 * SleepPredictionTuning.NAP_BUDGET_MINUTES_PER_NAP)
            .coerceAtMost(SleepPredictionTuning.NAP_BUDGET_MAX_SHIFT_MINUTES)
        assertEquals(
            Duration.ofMinutes(expectedShiftMinutes),
            factor.adjustment.abs(),
            "Deficit of 2 should produce 2x shift (capped at max)",
        )
    }

    @Test
    fun `adjustment is capped at NAP_BUDGET_MAX_SHIFT_MINUTES even for extreme deficit`() {
        // napCountToday = 0; ageInWeeks < 6 → getDefaultWakeWindows returns 5 naps → deficit could be large
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 0,
            ageInWeeks = 4, // < 6 weeks → 5 expected naps → deficit = 5
            nextType = SleepType.NAP,
        )
        assertTrue(
            factor.adjustment.abs().toMinutes() <= SleepPredictionTuning.NAP_BUDGET_MAX_SHIFT_MINUTES,
            "Adjustment must be capped at NAP_BUDGET_MAX_SHIFT_MINUTES for large deficits",
        )
    }

    @Test
    fun `reason string contains deficit count`() {
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 0,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        assertEquals(SleepReason.NapDeficit(deficit = 2), factor.reason)
    }

    @Test
    fun `negative surplus is neutral (baby had more naps than expected - routing already handles this)`() {
        // napCountToday = 3; expectedNaps = 2 → deficit = -1 → surplus
        val factor = NapBudgetFactor.adjustment(
            napCountToday = 3,
            ageInWeeks = ageInWeeks,
            nextType = SleepType.NAP,
        )
        assertEquals(
            SleepPredictionFactor.Neutral,
            factor,
            "Surplus (extra naps today) should not shift for NAP — routing already handles surplus via NIGHT_SLEEP path",
        )
    }
}
