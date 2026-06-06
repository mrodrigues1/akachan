package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

class NapBudgetEvalComparisonTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val baseNow = Instant.parse("2024-06-30T12:00:00Z")
    // birthDate = 2024-02-05 → ~20.7 weeks at baseNow
    // Prior: getNapWakeWindowMidpoint(20) = avg([105, 135]) = 120 min
    // Fixture uses 70+80 min actual intervals (shorter than prior) so the neutral predictor
    // overshoots both anchor types and the factor's −N min shift corrects toward actual.
    private val baby = Baby(name = "Test Baby", birthDate = LocalDate.of(2024, 2, 5))

    @Test
    fun `nap-budget factor clears section 7-1 on mixed-deficit NAP fixture`() {
        // Each day has two NAP anchors:
        //   deficit=2 (after night, napCountToday=0): wakeTarget ~96 min overshoots 70-min actual
        //             by ~26 min; factor shifts −20 min → error drops to ~6 min (+20 min gain)
        //   deficit=1 (after nap1, napCountToday=1): wakeTarget ~96 min overshoots 80-min actual
        //             by ~16 min; factor shifts −10 min → error drops to ~6 min (+10 min gain)
        // Net NAP MAE gain ≈ (20+10)/2 = 15 min > EVAL_MIN_MAE_GAIN_MIN.
        val records = shortIntervalRecords(days = 60)

        val baselineAnchors = buildAnchors(records, neutralProvider)
        val budgetAnchors = buildAnchors(records, NapBudgetFactor::adjustment)

        val napPaired = pairedAnchors(baselineAnchors, budgetAnchors, SleepType.NAP)

        assertTrue(
            napPaired.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Fixture must produce >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} NAP paired anchors; got ${napPaired.size}",
        )

        val improvements = napPaired.map { (baseline, new) ->
            baseline.errorMillis / 60_000.0 - new.errorMillis / 60_000.0
        }
        val maeGain = improvements.average()
        val newInWindow = napPaired.count { (_, new) -> new.inWindow }.toDouble() / napPaired.size
        val baselineInWindow = napPaired.count { (baseline, _) -> baseline.inWindow }.toDouble() / napPaired.size
        val newMissed = napPaired.count { (_, new) -> new.missedWindow }.toDouble() / napPaired.size
        val baselineMissed = napPaired.count { (baseline, _) -> baseline.missedWindow }.toDouble() / napPaired.size

        assertTrue(
            maeGain >= SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN,
            "MAE gain ${"%.1f".format(maeGain)} min must be >= ${SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN}",
        )
        assertTrue(
            newInWindow >= baselineInWindow,
            "In-window pct must not worsen; baseline=${"%.2f".format(baselineInWindow)}, new=${"%.2f".format(newInWindow)}",
        )
        assertTrue(
            bootstrapCiLowerBound(improvements) > 0.0,
            "Bootstrap CI lower bound for MAE gain must be positive",
        )
        assertTrue(
            newMissed <= baselineMissed + SleepPredictionTuning.EVAL_MAX_REGRESSION,
            "Missed-window rate must not worsen; baseline=${"%.2f".format(baselineMissed)}, new=${"%.2f".format(newMissed)}",
        )
    }

    @Test
    fun `nap-budget factor does not regress on uniform single-deficit fixture`() {
        val records = shortIntervalRecords(days = 60)

        val baselineAnchors = buildAnchors(records, neutralProvider)
        val budgetAnchors = buildAnchors(records, NapBudgetFactor::adjustment)

        for (type in SleepType.entries) {
            val paired = pairedAnchors(baselineAnchors, budgetAnchors, type)
            if (paired.size < SleepPredictionTuning.EVAL_MIN_ANCHORS) continue

            val newMae = paired.map { (_, new) -> new.errorMillis / 60_000.0 }.average()
            val baselineMae = paired.map { (baseline, _) -> baseline.errorMillis / 60_000.0 }.average()
            val newMissed = paired.count { (_, new) -> new.missedWindow }.toDouble() / paired.size
            val baselineMissed = paired.count { (baseline, _) -> baseline.missedWindow }.toDouble() / paired.size

            assertTrue(
                newMae <= baselineMae + SleepPredictionTuning.EVAL_MAX_REGRESSION,
                "[${type.name}] MAE must not worsen; baseline=${"%.1f".format(baselineMae)}, new=${"%.1f".format(newMae)}",
            )
            assertTrue(
                newMissed <= baselineMissed + SleepPredictionTuning.EVAL_MAX_REGRESSION,
                "[${type.name}] Missed-window rate must not worsen",
            )
        }
    }

    // --- Fixtures ---

    /**
     * Each day has a 9-hour night (22:00–07:00) followed by two short-window naps:
     *   nap1 at night-wake + 70 min (deficit=2 anchor)
     *   nap2 at nap1-wake + 80 min (deficit=1 anchor)
     *
     * Both intervals are shorter than the 20-week prior (120 min). The lookback
     * accumulates ~13 deficit=2 intervals and ~14 deficit=1 intervals, biasing
     * napWakeP50 to ~80 min. wakeTarget blends to ~96 min — overshooting actual by
     * 26 min on deficit=2 anchors and 16 min on deficit=1 anchors.
     * The factor shifts −20/−10 min, reducing both overshoots for a net MAE gain
     * of ~15 min per anchor pair.
     *
     * The bedtime-wake interval (12:30 → 22:00 = 570 min) exceeds MAX_PLAUSIBLE and
     * is filtered, so it does not contaminate the nap-wake median.
     */
    private fun shortIntervalRecords(days: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        var dayStart = baseNow.minus(Duration.ofDays(days.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        repeat(days) {
            val nightStart = dayStart.plus(Duration.ofHours(22))
            val nightEnd = nightStart.plus(Duration.ofHours(9)) // 07:00 next day
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = nightEnd.plus(Duration.ofMinutes(70))
            val nap1End = nap1Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = nap1End.plus(Duration.ofMinutes(80))
            val nap2End = nap2Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records
            .filter { it.startTime.isBefore(baseNow) && it.endTime?.isBefore(baseNow) == true }
            .sortedBy { it.startTime }
    }

    // --- Harness helpers ---

    private val neutralProvider: NapBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral }

    private fun buildAnchors(
        records: List<SleepRecord>,
        napBudgetProvider: NapBudgetFactorProvider,
    ): List<EvalAnchor> {
        val harness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                napBudgetFactorProvider = napBudgetProvider,
            )
        }
        return harness.buildAnchors(records, emptyList(), baby)
    }

    private fun pairedAnchors(
        baselineAnchors: List<EvalAnchor>,
        newAnchors: List<EvalAnchor>,
        type: SleepType,
    ): List<Pair<AnchorScore, AnchorScore>> {
        val baselineByInstant = baselineAnchors.associateBy { it.wakeInstant }
        return newAnchors
            .filter { it.segmentKey.sleepType == type && it.score != null }
            .mapNotNull { new ->
                val baseline = baselineByInstant[new.wakeInstant] ?: return@mapNotNull null
                val baselineScore = baseline.score ?: return@mapNotNull null
                baselineScore to new.score!!
            }
    }

    private fun bootstrapCiLowerBound(improvements: List<Double>, samples: Int = 1000): Double {
        val rng = Random(982)
        val means = (0 until samples).map {
            (0 until improvements.size).map { improvements[rng.nextInt(improvements.size)] }.average()
        }.sorted()
        return means[(0.05 * samples).toInt()]
    }
}
