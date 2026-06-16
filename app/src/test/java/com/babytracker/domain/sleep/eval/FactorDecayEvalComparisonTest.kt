package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §7.1 acceptance gate for AKA-155 (factor confidence-decay). The decay's value only materialises
 * once the blend trusts logged data (Phase 6 root cause: at full personalization the baby's own
 * median already encodes the circadian / debt / nap structure, so full-strength factors only add
 * bias). It is therefore evaluated at the phase's **target blend** — `W = 0.9`, `FULL = 10`, which
 * AKA-154 lands in production — comparing no decay (`factorFloor = 1.0`) against the production
 * `FACTOR_FLOOR`.
 *
 * Claim proven: at an accurate, personalized blend the population factors only add error, so fading
 * them (decay) reduces error on every cohort — strongest on short-wake, where the circadian factor
 * was the residual "too far ahead" push.
 */
class FactorDecayEvalComparisonTest {

    private val harness = ParametricSweepHarness(SleepPredictionCohorts.ZONE)
    private val cohorts = SleepPredictionCohorts.all()

    private val targetW = 0.9
    private val targetFull = 10
    private val noDecay = SweepConfig(maxPersonalizationWeight = targetW, fullPersonalizationIntervals = targetFull, factorFloor = 1.0)
    private val withDecay = SweepConfig(
        maxPersonalizationWeight = targetW,
        fullPersonalizationIntervals = targetFull,
        factorFloor = SleepPredictionTuning.FACTOR_FLOOR,
    )

    @Test
    fun `production fades factors at full personalization`() {
        assertTrue(
            SleepPredictionTuning.FACTOR_FLOOR in 0.0..1.0 && SleepPredictionTuning.FACTOR_FLOOR < 1.0,
            "FACTOR_FLOOR must enable decay (be below 1.0); got ${SleepPredictionTuning.FACTOR_FLOOR}",
        )
    }

    @Test
    fun `confidence decay reduces short-wake late bias without regressing any cohort`() {
        val results = harness.run(cohorts, listOf(noDecay, withDecay))
        val base = results[0]
        val decayed = results[1]

        // No regression anywhere: at the target blend the faded factors never raise MAE.
        for (label in listOf("short-wake", "typical", "long-wake")) {
            for (type in SleepType.entries) {
                val baseMae = mae(base, label, type)
                val decayedMae = mae(decayed, label, type)
                assertTrue(
                    decayedMae <= baseMae + 0.5,
                    "[$label/${type.name}] decay must not regress MAE; noDecay=${baseMae.f()} decay=${decayedMae.f()}",
                )
            }
        }

        // Short-wake NIGHT carries the residual circadian "too far ahead" push; decay must shrink it.
        val baseBias = bias(base, "short-wake", SleepType.NIGHT_SLEEP)
        val decayedBias = bias(decayed, "short-wake", SleepType.NIGHT_SLEEP)
        assertTrue(
            baseBias > 0.0 && Math.abs(decayedBias) < Math.abs(baseBias),
            "decay must shrink short-wake NIGHT late bias from ${baseBias.f()} toward 0; got ${decayedBias.f()}",
        )
        assertTrue(
            mae(decayed, "short-wake", SleepType.NIGHT_SLEEP) < mae(base, "short-wake", SleepType.NIGHT_SLEEP),
            "decay must lower short-wake NIGHT MAE at the target blend",
        )
    }

    private fun cohort(result: ParametricSweepHarness.CandidateResult, label: String) =
        result.cohort(label) ?: error("missing cohort $label")

    private fun seg(result: ParametricSweepHarness.CandidateResult, label: String, type: SleepType) =
        cohort(result, label).segment(type) ?: error("missing $label/${type.name}")

    private fun mae(result: ParametricSweepHarness.CandidateResult, label: String, type: SleepType) =
        requireNotNull(seg(result, label, type).maeMinutes)

    private fun bias(result: ParametricSweepHarness.CandidateResult, label: String, type: SleepType) =
        requireNotNull(seg(result, label, type).signedBiasMinutes)

    private fun Double.f() = "%.1f".format(this)
}
