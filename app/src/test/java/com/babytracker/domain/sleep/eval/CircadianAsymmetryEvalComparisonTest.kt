package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §7.1 acceptance gate for AKA-156 (asymmetric circadian). After the blend reweight (AKA-154) and
 * factor decay (AKA-155), a residual late bias remained on the NIGHT segment of the cohorts whose
 * own bedtime sits before the textbook slot — the circadian factor was still pushing them later.
 * The typical cohort's blend is exact (logged = age prior), so any NIGHT bias there is pure heuristic
 * residue: a sharp probe of the symptom. Capping the *later* push (AKA-156) clears it.
 *
 * The trigger condition for AKA-156 was met (typical NIGHT residual ~+12 min late at production);
 * the prior-nudge half of the issue's scope was not needed — the residue was circadian, not prior.
 */
class CircadianAsymmetryEvalComparisonTest {

    private val harness = ParametricSweepHarness(SleepPredictionCohorts.ZONE)

    @Test
    fun `asymmetric circadian leaves only a small late residual and never pushes bedtime later than the baby`() {
        val result = harness.run(SleepPredictionCohorts.all(), listOf(SweepConfig())).first()

        val typicalNight = requireNotNull(result.cohort("typical")?.segment(SleepType.NIGHT_SLEEP)?.signedBiasMinutes)
        val shortNight = requireNotNull(result.cohort("short-wake")?.segment(SleepType.NIGHT_SLEEP)?.signedBiasMinutes)
        val longNight = requireNotNull(result.cohort("long-wake")?.segment(SleepType.NIGHT_SLEEP)?.signedBiasMinutes)

        // Typical: blend is exact, so this is pure factor residue — must be tightly bounded.
        assertTrue(
            Math.abs(typicalNight) <= 6.0,
            "typical NIGHT residual must be small (got ${"%.1f".format(typicalNight)} min); widening the later cap regresses it",
        )
        // Short-wake: still mildly late (blend residue + small later nudge), but well under the half-window grace.
        assertTrue(
            shortNight in 0.0..10.0,
            "short-wake NIGHT must be only mildly late (got ${"%.1f".format(shortNight)} min)",
        )
        // Long-wake already sits past the slot; the factor may only pull it earlier, never later.
        assertTrue(
            longNight < 0.0,
            "long-wake NIGHT must not be pushed later (got ${"%.1f".format(longNight)} min)",
        )
    }
}
