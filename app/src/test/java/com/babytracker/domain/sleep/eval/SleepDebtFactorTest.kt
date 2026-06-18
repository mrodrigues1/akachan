package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

class SleepDebtFactorTest {

    private val ageInWeeks = 20 // getTotalSleepRecommendation: 12h–16h → midpoint 14h = 50400000 ms

    @Test
    fun `neutral when sleepLast24h equals personalized target`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = targetMillis,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `neutral when debt is below SLEEP_DEBT_MIN_HOURS threshold`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val slightlyUnder = targetMillis - Duration.ofMinutes(30).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = slightlyUnder,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `under-slept produces negative adjustment (shift earlier)`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val underSlept = targetMillis - Duration.ofHours(2).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = underSlept,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(
            factor.adjustment < Duration.ZERO,
            "Under-slept baby should have a negative (earlier) adjustment, got ${factor.adjustment}",
        )
    }

    @Test
    fun `over-slept produces positive adjustment (shift later)`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val overSlept = targetMillis + Duration.ofHours(3).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = overSlept,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(
            factor.adjustment > Duration.ZERO,
            "Over-slept baby should have a positive (later) adjustment, got ${factor.adjustment}",
        )
    }

    @Test
    fun `adjustment is capped at SLEEP_DEBT_MAX_SHIFT_MINUTES`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val severelyUnderSlept = targetMillis - Duration.ofHours(10).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = severelyUnderSlept,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(
            factor.adjustment.abs().toMinutes() <= SleepPredictionTuning.SLEEP_DEBT_MAX_SHIFT_MINUTES,
            "Adjustment magnitude must not exceed SLEEP_DEBT_MAX_SHIFT_MINUTES",
        )
    }

    @Test
    fun `uses age-prior only when avgDailySleepMillis is null`() {
        val agePriorMidpointMillis = Duration.ofHours(14).toMillis()
        val underSlept = agePriorMidpointMillis - Duration.ofHours(2).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = underSlept,
            avgDailySleepMillis = null,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(
            factor.adjustment < Duration.ZERO,
            "Should still shift earlier vs. age prior when avgDailySleepMillis is null",
        )
    }

    @Test
    fun `personalized target blends age prior and personal average at 50-50`() {
        val agePriorMidpointMillis = Duration.ofHours(14).toMillis()
        val personalAvgMillis = Duration.ofHours(12).toMillis()
        val blendedTargetMillis = (agePriorMidpointMillis + personalAvgMillis) / 2 // 13h
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = blendedTargetMillis,
            avgDailySleepMillis = personalAvgMillis,
            ageInWeeks = ageInWeeks,
        )
        assertEquals(SleepPredictionFactor.Neutral, factor)
    }

    @Test
    fun `factor reason contains direction word when factor is active`() {
        val targetMillis = Duration.ofHours(14).toMillis()
        val underSlept = targetMillis - Duration.ofHours(2).toMillis()
        val factor = SleepDebtFactor.adjustment(
            sleepLast24hMillis = underSlept,
            avgDailySleepMillis = targetMillis,
            ageInWeeks = ageInWeeks,
        )
        assertTrue(
            factor.reason is SleepReason.SleepDebt,
            "Active factor must surface a sleep-debt reason",
        )
    }
}
