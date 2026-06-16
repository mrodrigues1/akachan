package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * §7.1 acceptance gate for AKA-154 (blend reweighting). The chosen constants
 * `MAX_PERSONALIZATION_WEIGHT = 0.9` / `FULL_PERSONALIZATION_INTERVALS = 10` were selected by the
 * AKA-153 sweep over `W ∈ {0.6..0.9} × FULL ∈ {8,10,12,14}` (factors held at full strength), under
 * the rule: minimize short-wake MAE subject to no regression on typical / long-wake.
 *
 * `chosen` is [SweepConfig] reading the live production constants; the
 * [SleepPredictionCohortsTest] parity test proves it scores identically to `SleepWindowPredictor`,
 * so this gate runs on the real shipped values. `baseline` pins the pre-Phase-6 blend (0.6 / 14).
 */
class BlendReweightEvalComparisonTest {

    private val harness = ParametricSweepHarness(SleepPredictionCohorts.ZONE)
    private val cohorts = SleepPredictionCohorts.all()

    private val baseline = SweepConfig(maxPersonalizationWeight = 0.6, fullPersonalizationIntervals = 14)
    private val chosen = SweepConfig() // production: MAX_PERSONALIZATION_WEIGHT / FULL_PERSONALIZATION_INTERVALS

    @Test
    fun `chosen blend matches the live production constants`() {
        assertTrue(chosen.maxPersonalizationWeight == SleepPredictionTuning.MAX_PERSONALIZATION_WEIGHT)
        assertTrue(chosen.fullPersonalizationIntervals == SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
        assertTrue(
            SleepPredictionTuning.MAX_PERSONALIZATION_WEIGHT >= 0.9,
            "blend reweight must raise personalization weight to at least 0.9",
        )
    }

    @Test
    fun `short-wake MAE improves by at least the acceptance threshold`() {
        val (base, now) = evaluate("short-wake")
        val pooledGain = pooledMae(base) - pooledMae(now)
        assertTrue(
            pooledGain >= SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN,
            "short-wake pooled MAE must improve by >= ${SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN} min; " +
                "baseline=${pooledMae(base).f()} new=${pooledMae(now).f()} gain=${pooledGain.f()}",
        )
        val nightGain = mae(base, SleepType.NIGHT_SLEEP) - mae(now, SleepType.NIGHT_SLEEP)
        assertTrue(
            nightGain >= SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN,
            "short-wake NIGHT MAE (the 'too far ahead' symptom) must improve by >= " +
                "${SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN} min; gain=${nightGain.f()}",
        )
    }

    @Test
    fun `typical and long-wake cohorts do not regress`() {
        for (label in listOf("typical", "long-wake")) {
            val (base, now) = evaluate(label)
            for (type in SleepType.entries) {
                val regression = mae(now, type) - mae(base, type)
                assertTrue(
                    regression <= SleepPredictionTuning.EVAL_MAX_REGRESSION,
                    "[$label/${type.name}] MAE must not regress beyond ${SleepPredictionTuning.EVAL_MAX_REGRESSION}; " +
                        "baseline=${mae(base, type).f()} new=${mae(now, type).f()}",
                )
                assertTrue(
                    missed(now, type) <= SleepPredictionTuning.EVAL_ADVERSE_COHORT_MISSED_RATE_CAP,
                    "[$label/${type.name}] missed-window rate ${missed(now, type).f()} must be within " +
                        "${SleepPredictionTuning.EVAL_ADVERSE_COHORT_MISSED_RATE_CAP}",
                )
            }
        }
    }

    private fun evaluate(label: String): Pair<ParametricSweepHarness.CohortResult, ParametricSweepHarness.CohortResult> {
        val results = harness.run(cohorts, listOf(baseline, chosen))
        return results[0].cohort(label)!! to results[1].cohort(label)!!
    }

    private fun seg(result: ParametricSweepHarness.CohortResult, type: SleepType) =
        result.segment(type) ?: error("missing ${type.name} segment for ${result.cohortLabel}")

    private fun mae(result: ParametricSweepHarness.CohortResult, type: SleepType): Double =
        requireNotNull(seg(result, type).maeMinutes) { "no MAE for ${result.cohortLabel}/${type.name}" }

    private fun missed(result: ParametricSweepHarness.CohortResult, type: SleepType): Double =
        requireNotNull(seg(result, type).missedRate) { "no missed-rate for ${result.cohortLabel}/${type.name}" }

    private fun pooledMae(result: ParametricSweepHarness.CohortResult): Double {
        val nap = seg(result, SleepType.NAP)
        val night = seg(result, SleepType.NIGHT_SLEEP)
        val total = nap.anchorCount + night.anchorCount
        return ((nap.maeMinutes ?: 0.0) * nap.anchorCount + (night.maeMinutes ?: 0.0) * night.anchorCount) / total
    }

    private fun Double.f() = "%.1f".format(this)
}
