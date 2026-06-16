package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * AKA-153 — the parametric sweep harness reports deterministic per-candidate / per-segment metrics
 * and can detect a configuration that reduces the short-wake late bias. The *chosen* constants are
 * fixed by Issues 2 ([AKA-154]) and 3 ([AKA-155]); this test only proves the search tool works.
 */
class ParametricSweepHarnessTest {

    private val harness = ParametricSweepHarness(SleepPredictionCohorts.ZONE)
    private val cohorts = SleepPredictionCohorts.all()

    @Test
    fun `sweep reports metrics for every cohort and segment, deterministically`() {
        val configs = listOf(
            SweepConfig(),
            SweepConfig(maxPersonalizationWeight = 0.9, fullPersonalizationIntervals = 8),
        )

        val first = harness.run(cohorts, configs)
        val second = harness.run(cohorts, configs)

        assertEquals(configs.size, first.size, "one result per candidate config")
        for (candidate in first) {
            assertEquals(cohorts.size, candidate.cohorts.size, "one result per cohort")
            for (cohort in candidate.cohorts) {
                for (type in SleepType.entries) {
                    val segment = cohort.segment(type)
                    assertNotNull(segment, "[${cohort.cohortLabel}/${type.name}] segment must be reported")
                    assertTrue(
                        segment!!.anchorCount >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
                        "[${cohort.cohortLabel}/${type.name}] must have >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} anchors; got ${segment.anchorCount}",
                    )
                    assertNotNull(segment.maeMinutes, "[${cohort.cohortLabel}/${type.name}] MAE must be reported")
                    assertNotNull(segment.signedBiasMinutes, "[${cohort.cohortLabel}/${type.name}] signed bias must be reported")
                }
            }
        }
        assertEquals(first, second, "sweep must be fully deterministic across runs")
    }

    @Test
    fun `a higher-personalization, faded-factor candidate reduces short-wake late bias vs default`() {
        val default = SweepConfig()
        // Direction Issues 2 & 3 will tune: trust logged data more, fade the heuristic factors.
        val candidate = SweepConfig(
            maxPersonalizationWeight = 0.9,
            fullPersonalizationIntervals = 8,
            factorFloor = 0.0,
        )

        val results = harness.run(cohorts, listOf(default, candidate))
        val defaultNight = results[0].cohort("short-wake")!!.segment(SleepType.NIGHT_SLEEP)!!
        val candidateNight = results[1].cohort("short-wake")!!.segment(SleepType.NIGHT_SLEEP)!!

        val defaultBias = requireNotNull(defaultNight.signedBiasMinutes)
        val candidateBias = requireNotNull(candidateNight.signedBiasMinutes)
        val defaultMae = requireNotNull(defaultNight.maeMinutes)
        val candidateMae = requireNotNull(candidateNight.maeMinutes)

        assertTrue(
            defaultBias > 0.0,
            "sanity: default config must show the late bias (got ${"%.1f".format(defaultBias)})",
        )
        assertTrue(
            Math.abs(candidateBias) < Math.abs(defaultBias),
            "candidate must shrink short-wake NIGHT bias from ${"%.1f".format(defaultBias)} " +
                "toward 0; got ${"%.1f".format(candidateBias)}",
        )
        assertTrue(
            candidateMae < defaultMae,
            "candidate must lower short-wake NIGHT MAE from ${"%.1f".format(defaultMae)} " +
                "to ${"%.1f".format(candidateMae)}",
        )
    }
}
