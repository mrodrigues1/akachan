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

class SleepDebtEvalComparisonTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val baseNow = Instant.parse("2024-06-30T12:00:00Z")
    private val baby = Baby(name = "Test Baby", birthDate = LocalDate.of(2024, 1, 1))

    // ageInWeeks ≈ 25w → getTotalSleepRecommendation returns 12–16h (midpoint 14h)

    @Test
    fun `sleep-debt factor clears section 7-1 on phased sleep-deprived bedtime fixture`() {
        // Phase 1 (normalDays): well-rested baseline — predictor learns normal bedtime intervals.
        // Phase 2 (deprivedDays): short nights → low sleepLast24h → debt factor shifts bedtime
        // earlier, matching the 30-min-earlier actual onset produced by accumulated sleep debt.
        val records = phasedDeprivedRecords(normalDays = 20, deprivedDays = 30)

        val baselineAnchors = buildAnchors(records, neutralDebtProvider)
        val debtAnchors = buildAnchors(records, SleepDebtFactor::adjustment)

        val bedtimePaired = pairedAnchors(baselineAnchors, debtAnchors, SleepType.NIGHT_SLEEP)

        assertTrue(
            bedtimePaired.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Fixture must produce >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} bedtime anchors; got ${bedtimePaired.size}",
        )
        val improvements = bedtimePaired.map { (baseline, new) ->
            baseline.errorMillis / 60_000.0 - new.errorMillis / 60_000.0
        }
        val maeGain = improvements.average()
        val newInWindow = bedtimePaired.count { (_, new) -> new.inWindow }.toDouble() / bedtimePaired.size
        val baselineInWindow = bedtimePaired.count { (baseline, _) -> baseline.inWindow }.toDouble() / bedtimePaired.size
        val newMissed = bedtimePaired.count { (_, new) -> new.missedWindow }.toDouble() / bedtimePaired.size
        val baselineMissed = bedtimePaired.count { (baseline, _) -> baseline.missedWindow }.toDouble() / bedtimePaired.size

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
    fun `sleep-debt factor does not regress on well-rested stable fixture`() {
        val records = stableWellRestedRecords(days = 60)

        val baselineAnchors = buildAnchors(records, neutralDebtProvider)
        val debtAnchors = buildAnchors(records, SleepDebtFactor::adjustment)

        for (type in SleepType.entries) {
            val paired = pairedAnchors(baselineAnchors, debtAnchors, type)
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
     * Phase 1 (days 0..<normalDays): well-rested nights, 9h30min (18:30–04:00).
     * Phase 2 (days normalDays..<total): short nights, 6h (18:00–00:00).
     *
     * Both night types end before 04:30, so the NIGHT→NAP1 gap (> 6h) is filtered from
     * the combined wake-interval IQR. Only NAP1→NAP2 (180min, stable) and NAP2→NIGHT
     * (120min normal / 90min deprived, diff = 30min ≤ INSTABILITY_CEILING) contribute.
     * With ≤ 4 deprived nights in any 14-day lookback, the bedtime IQR stays at 30min.
     */
    private fun phasedDeprivedRecords(normalDays: Int, deprivedDays: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        val totalDays = normalDays + deprivedDays
        var dayStart = baseNow.minus(Duration.ofDays(totalDays.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        repeat(totalDays) { dayIndex ->
            val isDeprived = dayIndex >= normalDays

            // Normal night: 18:30 → 04:00+1 (9h30min)  NAP2→NIGHT = 120min
            // Deprived night: 18:00 → 00:00+1 (6h)      NAP2→NIGHT =  90min  (30min earlier)
            val nightStart = if (isDeprived) {
                dayStart.plus(Duration.ofHours(18))
            } else {
                dayStart.plus(Duration.ofHours(18)).plus(Duration.ofMinutes(30))
            }
            val nightDuration = if (isDeprived) {
                Duration.ofHours(6)
            } else {
                Duration.ofHours(9).plus(Duration.ofMinutes(30))
            }
            records += SleepRecord(id++, nightStart, nightStart.plus(nightDuration), SleepType.NIGHT_SLEEP)

            // NAP1 and NAP2 are identical across both conditions so NAP-type IQR = 0.
            val nap1Start = dayStart.plus(Duration.ofHours(10)).plus(Duration.ofMinutes(30))
            val nap1End = nap1Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(15))
            val nap2End = nap2Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records.filter { it.startTime.isBefore(baseNow) && it.endTime != null && it.endTime.isBefore(baseNow) }
            .sortedBy { it.startTime }
    }

    private fun stableWellRestedRecords(days: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        var dayStart = baseNow.minus(Duration.ofDays(days.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()

        repeat(days) {
            val nightStart = dayStart.plus(Duration.ofHours(20))
            val nightEnd = nightStart.plus(Duration.ofHours(11))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = dayStart.plus(Duration.ofHours(10))
            val nap1End = nap1Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(14)).plus(Duration.ofMinutes(30))
            val nap2End = nap2Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records.filter { it.startTime.isBefore(baseNow) && it.endTime != null && it.endTime.isBefore(baseNow) }
            .sortedBy { it.startTime }
    }

    // --- Harness helpers ---

    private val neutralDebtProvider: SleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral }

    private fun buildAnchors(
        records: List<SleepRecord>,
        debtProvider: SleepDebtFactorProvider,
    ): List<EvalAnchor> {
        val harness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                sleepDebtFactorProvider = debtProvider,
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
        val rng = Random(98)
        val means = (0 until samples).map {
            (0 until improvements.size).map { improvements[rng.nextInt(improvements.size)] }.average()
        }.sorted()
        return means[(0.05 * samples).toInt()]
    }
}
