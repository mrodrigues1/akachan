package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import com.babytracker.domain.sleep.feature.SleepFeatures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * AKA-153 — proves the synthetic cohorts are well-formed and that the *current* algorithm exhibits
 * the user-reported "window lands too far ahead" symptom on the short-wake cohort. Also pins the
 * parametric mirror ([sweepPredict]) to production via a default-config parity assertion.
 */
class SleepPredictionCohortsTest {

    private val zone = SleepPredictionCohorts.ZONE

    /** The live production algorithm: blend + all three heuristic factors. */
    private val productionPredict: (SleepFeatures, Int, Instant) -> SleepPredictionState =
        { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                circadianFactorProvider = CircadianBiasFactor::adjustment,
                sleepDebtFactorProvider = SleepDebtFactor::adjustment,
                napBudgetFactorProvider = NapBudgetFactor::adjustment,
            )
        }

    // The pre-Phase-6 algorithm (60% personalization cap, 14-interval ramp, full-strength factors).
    // The late-bias tests below assert the *symptom that motivated this phase* against this frozen
    // baseline — live production (AKA-154 blend + AKA-155 decay) has since corrected it.
    private val legacyBlendPredict: (SleepFeatures, Int, Instant) -> SleepPredictionState =
        { features, ageInWeeks, now ->
            sweepPredict(
                SweepConfig(maxPersonalizationWeight = 0.6, fullPersonalizationIntervals = 14, factorFloor = 1.0),
                features, ageInWeeks, now,
            )
        }

    @Test
    fun `cohorts reproduce intended wake windows and pass the quality gate`() {
        for (cohort in SleepPredictionCohorts.all()) {
            // Snapshot features at the final wake so the lookback is full and personalized.
            val lastWake = cohort.records.last().endTime!!
            val lookbackStart = lastWake.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
            val recent = cohort.records.filter { it.startTime >= lookbackStart && it.startTime < lastWake }
            val features = SleepFeatureExtractor(Clock.fixed(lastWake, zone), zone).extract(recent, emptyList())
            val metrics = features.metrics
            val tag = cohort.params.label

            assertTrue(
                features.quality.hasSufficientZoneIndependentEvidence &&
                    features.quality.isLocalDayCoverageSufficient,
                "[$tag] must pass the quality gate",
            )

            val tolerance = Duration.ofMinutes(6).toMillis()
            assertTrue(
                metrics.napStats.p50Millis != null &&
                    Math.abs(metrics.napStats.p50Millis - Duration.ofMinutes(cohort.params.napWakeMin).toMillis()) <= tolerance,
                "[$tag] napWakeP50 must be ~${cohort.params.napWakeMin} min; got ${metrics.napStats.p50Millis?.div(60_000)}",
            )
            assertTrue(
                metrics.bedtimeStats.p50Millis != null &&
                    Math.abs(metrics.bedtimeStats.p50Millis - Duration.ofMinutes(cohort.params.bedtimeWakeMin).toMillis()) <= tolerance,
                "[$tag] bedtimeWakeP50 must be ~${cohort.params.bedtimeWakeMin} min; got ${metrics.bedtimeStats.p50Millis?.div(60_000)}",
            )

            // Personalizes strongly: bedtime is the slower type (~1 interval/day), still near saturation.
            val bedtimeC = metrics.bedtimeStats.count.toFloat() /
                SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS
            assertTrue(
                bedtimeC >= 0.85f,
                "[$tag] bedtime qualityC must be >= 0.85 (got $bedtimeC; ${metrics.bedtimeStats.count} intervals)",
            )
        }
    }

    @Test
    fun `every scored anchor stays inside the 16-24w age band`() {
        for (cohort in SleepPredictionCohorts.all()) {
            val anchors = SleepEvalHarness(zone, productionPredict)
                .buildAnchors(cohort.records, emptyList(), cohort.baby)
                .filter { cohort.isScoredWake(it.wakeInstant) }
            for (anchor in anchors) {
                val ageWeeks = ChronoUnit.WEEKS.between(
                    cohort.baby.birthDate,
                    anchor.wakeInstant.atZone(zone).toLocalDate(),
                ).toInt()
                assertTrue(
                    ageWeeks in 16 until 24,
                    "[${cohort.params.label}] anchor age $ageWeeks must stay in [16,24) for constant priors",
                )
            }
        }
    }

    @Test
    fun `pre-Phase-6 algorithm lands the window too far ahead on the short-wake cohort`() {
        val short = SleepPredictionCohorts.cohort(SleepPredictionCohorts.SHORT_WAKE)

        val nightBias = signedBias(short, SleepType.NIGHT_SLEEP)
        assertTrue(
            nightBias.count >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "short-wake must yield >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} NIGHT anchors; got ${nightBias.count}",
        )
        // The symptom: the prior + circadian factor drag bedtime well past the baby's real onset.
        assertTrue(
            nightBias.meanMinutes >= 10.0,
            "short-wake NIGHT predictions must be measurably late (mean signed bias " +
                "${"%.1f".format(nightBias.meanMinutes)} min must be >= 10). The eval cannot detect the symptom.",
        )

        val napBias = signedBias(short, SleepType.NAP)
        assertTrue(
            napBias.count >= SleepPredictionTuning.EVAL_MIN_ANCHORS && napBias.meanMinutes > 0.0,
            "short-wake NAP predictions must also be late (mean ${"%.1f".format(napBias.meanMinutes)} min must be > 0)",
        )
    }

    @Test
    fun `late bias is strongest for short-wake and not present for long-wake`() {
        val short = signedBias(SleepPredictionCohorts.cohort(SleepPredictionCohorts.SHORT_WAKE), SleepType.NIGHT_SLEEP)
        val typical = signedBias(SleepPredictionCohorts.cohort(SleepPredictionCohorts.TYPICAL), SleepType.NIGHT_SLEEP)
        val long = signedBias(SleepPredictionCohorts.cohort(SleepPredictionCohorts.LONG_WAKE), SleepType.NIGHT_SLEEP)

        assertTrue(
            short.meanMinutes > typical.meanMinutes,
            "short-wake NIGHT bias ${"%.1f".format(short.meanMinutes)} must exceed typical ${"%.1f".format(typical.meanMinutes)}",
        )
        assertTrue(
            long.meanMinutes < short.meanMinutes,
            "long-wake NIGHT bias ${"%.1f".format(long.meanMinutes)} must be below short-wake " +
                "${"%.1f".format(short.meanMinutes)} (opposite-direction guard)",
        )
    }

    @Test
    fun `sweep default config reproduces production scoring exactly (parity)`() {
        val defaultConfig = SweepConfig()
        for (cohort in SleepPredictionCohorts.all()) {
            val productionAnchors = SleepEvalHarness(zone, productionPredict)
                .buildAnchors(cohort.records, emptyList(), cohort.baby)
                .associateBy { it.wakeInstant }
            val sweepAnchors = SleepEvalHarness(zone) { f, a, n -> sweepPredict(defaultConfig, f, a, n) }
                .buildAnchors(cohort.records, emptyList(), cohort.baby)

            var compared = 0
            for (sweep in sweepAnchors) {
                if (!cohort.isScoredWake(sweep.wakeInstant)) continue
                val prod = productionAnchors.getValue(sweep.wakeInstant)
                assertEquals(
                    prod.predictedState::class, sweep.predictedState::class,
                    "[${cohort.params.label}] state class must match production at ${sweep.wakeInstant}",
                )
                assertEquals(
                    prod.score, sweep.score,
                    "[${cohort.params.label}] score must match production at ${sweep.wakeInstant}",
                )
                compared++
            }
            assertTrue(
                compared >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
                "[${cohort.params.label}] parity must compare >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} anchors; got $compared",
            )
        }
    }

    private data class Bias(val meanMinutes: Double, val count: Int)

    private fun signedBias(cohort: SleepPredictionCohorts.Cohort, type: SleepType): Bias {
        val anchors = SleepEvalHarness(zone, legacyBlendPredict)
            .buildAnchors(cohort.records, emptyList(), cohort.baby)
            .filter { it.segmentKey.sleepType == type && cohort.isScoredWake(it.wakeInstant) }
        val biases = anchors.mapNotNull { anchor ->
            val window = (anchor.predictedState as? SleepPredictionState.Window)?.window ?: return@mapNotNull null
            val actual = cohort.actualOnsetByWake.getValue(anchor.wakeInstant)
            (window.bestEstimate.toEpochMilli() - actual.toEpochMilli()) / 60_000.0
        }
        return Bias(if (biases.isEmpty()) 0.0 else biases.average(), biases.size)
    }
}
