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
    // 20 weeks → getScheduledNapCount = 2; typical nap wake-window midpoint = 120 min
    private val baby = Baby(name = "Test Baby", birthDate = LocalDate.of(2024, 2, 5))

    @Test
    fun `nap-budget factor clears section 7-1 on mixed-deficit NAP fixture`() {
        val records = mixedDeficitRecords(lowDeficitDays = 30, highDeficitDays = 30)

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
        val records = uniformSingleDeficitRecords(days = 60)

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
     * Alternates between:
     * - Low-deficit days: baby had nap1 at the normal time; anchor is after nap1, predicting nap2 (deficit=1)
     * - High-deficit days: baby skipped nap1; anchor is at mid-day, predicting nap1 from a zero-nap start (deficit=2)
     *   On high-deficit days, the actual next nap is 20 min earlier than the low-deficit baseline would predict.
     */
    private fun mixedDeficitRecords(lowDeficitDays: Int, highDeficitDays: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        val totalDays = lowDeficitDays + highDeficitDays
        var dayStart = baseNow.minus(Duration.ofDays(totalDays.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        repeat(totalDays) { dayIndex ->
            val isHighDeficit = dayIndex % 2 == 1

            // Night sleep: always same (20:00–07:00 = 11h)
            val nightStart = dayStart.plus(Duration.ofHours(20))
            val nightEnd = nightStart.plus(Duration.ofHours(11))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            if (!isHighDeficit) {
                // Low-deficit: normal nap1 (09:30–11:00), normal nap2 (14:00–15:30)
                val nap1Start = dayStart.plus(Duration.ofHours(9)).plus(Duration.ofMinutes(30))
                val nap1End = nap1Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

                val nap2Start = dayStart.plus(Duration.ofHours(14))
                val nap2End = nap2Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)
            } else {
                // High-deficit: nap1 skipped; nap2 starts 20 min earlier than median interval would predict
                // Median wake interval from normal days: nap1 ends 11:00, nap2 starts 14:00 → 3h interval
                // High-deficit: baby woke from night at 07:00; 3h would give 10:00 but baby is tired → 09:40
                val nap2Start = dayStart.plus(Duration.ofHours(9)).plus(Duration.ofMinutes(40))
                val nap2End = nap2Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

                // Second nap of the high-deficit day: normal timing relative to first nap
                val nap3Start = dayStart.plus(Duration.ofHours(14))
                val nap3End = nap3Start.plus(Duration.ofMinutes(90))
                records += SleepRecord(id++, nap3Start, nap3End, SleepType.NAP)
            }

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records
            .filter { it.startTime.isBefore(baseNow) && it.endTime != null && it.endTime!!.isBefore(baseNow) }
            .sortedBy { it.startTime }
    }

    /**
     * Uniform days: baby always has nap1 at 09:30, nap2 at 14:00, bedtime at 20:00.
     * At every NAP anchor, deficit = 1 (after nap1, predicting nap2) — consistent across all days.
     * The factor always applies the same shift, so MAE should be neutral or minimally changed.
     */
    private fun uniformSingleDeficitRecords(days: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        var dayStart = baseNow.minus(Duration.ofDays(days.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        repeat(days) {
            val nightStart = dayStart.plus(Duration.ofHours(20))
            val nightEnd = nightStart.plus(Duration.ofHours(11))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = dayStart.plus(Duration.ofHours(9)).plus(Duration.ofMinutes(30))
            val nap1End = nap1Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(14))
            val nap2End = nap2Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records
            .filter { it.startTime.isBefore(baseNow) && it.endTime != null && it.endTime!!.isBefore(baseNow) }
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
