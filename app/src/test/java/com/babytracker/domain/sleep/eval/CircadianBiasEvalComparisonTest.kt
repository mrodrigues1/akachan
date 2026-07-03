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

class CircadianBiasEvalComparisonTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    private val baseNow = Instant.parse("2024-06-30T12:00:00Z")
    private val baby = Baby(name = "Test Baby", birthDate = LocalDate.of(2024, 2, 14))

    private val neutralCircadian: CircadianFactorProvider = { _, _, _, _ -> SleepPredictionFactor.Neutral }
    private val neutralSleepDebt: SleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral }
    private val neutralNapBudget: NapBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral }

    // Phase 6: a factor is demonstrated against a prior-leaning blend (an as-yet-unknown baby, where
    // qualityC → 0 so the blend leans on the age prior and the confidence decay leaves the factor at
    // full strength). That is the regime where the population heuristics carry weight. At the
    // production blend (W = 0.9) a data-rich baby's own bedtime median already encodes the circadian
    // skew, so the now-decayed factor contributes little; that regime is gated by the cohort tests.
    private val unknownBabyBlend = SweepConfig(maxPersonalizationWeight = 0.3, factorFloor = 1.0)

    @Test
    fun `circadian bias clears section 7-1 acceptance criteria on bedtime-skew fixture`() {
        val records = bedtimeDriftRecords(historyDays = 14, evaluatedDays = 10)
        val baselineHarness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            sweepPredict(
                unknownBabyBlend, features, ageInWeeks, now,
                circadian = neutralCircadian,
                sleepDebt = neutralSleepDebt,
                napBudget = neutralNapBudget,
            )
        }
        val newHarness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            sweepPredict(
                unknownBabyBlend, features, ageInWeeks, now,
                circadian = CircadianBiasFactor::adjustment,
                sleepDebt = neutralSleepDebt,
                napBudget = neutralNapBudget,
            )
        }
        val baselineAnchors = baselineHarness.buildAnchors(records, emptyList(), baby).associateBy { it.wakeInstant }
        val newAnchors = newHarness.buildAnchors(records, emptyList(), baby)
        val bedtimeAnchors = newAnchors.filter { it.segmentKey.sleepType == SleepType.NIGHT_SLEEP }

        assertTrue(
            bedtimeAnchors.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Fixture must produce at least ${SleepPredictionTuning.EVAL_MIN_ANCHORS} bedtime anchors; got ${bedtimeAnchors.size}",
        )

        val newNullBedtimeCount = bedtimeAnchors.count { it.score == null }
        val baselineNullBedtimeCount = bedtimeAnchors.count { baselineAnchors[it.wakeInstant]?.score == null }
        assertTrue(
            newNullBedtimeCount <= baselineNullBedtimeCount,
            "Circadian predictor must not degrade Window availability; new nulls=$newNullBedtimeCount baseline=$baselineNullBedtimeCount",
        )

        val paired = bedtimeAnchors.mapNotNull { newAnchor ->
            val baselineAnchor = baselineAnchors[newAnchor.wakeInstant] ?: return@mapNotNull null
            val newScore = newAnchor.score ?: return@mapNotNull null
            val baselineScore = baselineAnchor.score ?: return@mapNotNull null
            newScore to baselineScore
        }

        assertTrue(
            paired.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Need at least ${SleepPredictionTuning.EVAL_MIN_ANCHORS} paired bedtime scores where both predictors returned Window; got ${paired.size}",
        )

        val improvements = paired.map { (newScore, baselineScore) ->
            baselineScore.errorMillis / 60_000.0 - newScore.errorMillis / 60_000.0
        }
        val maeGain = improvements.average()
        val newInWindowPct = paired.count { (newScore, _) -> newScore.inWindow }.toDouble() / paired.size
        val baselineInWindowPct = paired.count { (_, baselineScore) -> baselineScore.inWindow }.toDouble() / paired.size
        val newMissedRate = paired.count { (newScore, _) -> newScore.missedWindow }.toDouble() / paired.size
        val baselineMissedRate = paired.count { (_, baselineScore) -> baselineScore.missedWindow }.toDouble() / paired.size

        assertTrue(
            maeGain >= SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN,
            "MAE gain ${"%.1f".format(maeGain)} min must be >= ${SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN}",
        )
        assertTrue(
            newInWindowPct > baselineInWindowPct,
            "In-window percent must improve; baseline=${"%.2f".format(baselineInWindowPct)}, new=${"%.2f".format(newInWindowPct)}",
        )
        assertTrue(
            bootstrapCiLowerBound(improvements) > 0.0,
            "Bootstrap CI lower bound for MAE improvement must be positive",
        )
        assertTrue(
            newMissedRate <= baselineMissedRate + SleepPredictionTuning.EVAL_MAX_REGRESSION,
            "Missed-window rate must not worsen; baseline=${"%.2f".format(baselineMissedRate)}, new=${"%.2f".format(newMissedRate)}",
        )
    }

    @Test
    fun `circadian bias does not regress stable personalized late-bedtime fixture`() {
        val records = stableLateBedtimeRecords(days = 35)
        val paired = pairedBedtimeScores(records)

        val newMaeMin = paired.map { (newScore, _) -> newScore.errorMillis / 60_000.0 }.average()
        val baselineMaeMin = paired.map { (_, baselineScore) -> baselineScore.errorMillis / 60_000.0 }.average()
        val newMissedRate = paired.count { (newScore, _) -> newScore.missedWindow }.toDouble() / paired.size
        val baselineMissedRate = paired.count { (_, baselineScore) -> baselineScore.missedWindow }.toDouble() / paired.size

        val regressionMin = newMaeMin - baselineMaeMin
        assertTrue(
            regressionMin <= SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES.toDouble(),
            "Adverse cohort MAE regression (${"%.1f".format(regressionMin)} min) must not exceed circadian cap (${SleepPredictionTuning.CIRCADIAN_MAX_SHIFT_MINUTES} min)",
        )
        assertTrue(
            newMissedRate <= baselineMissedRate + SleepPredictionTuning.EVAL_ADVERSE_COHORT_MISSED_RATE_CAP,
            "Adverse cohort missed-window rate must not worsen beyond cap tolerance; baseline=${"%.2f".format(baselineMissedRate)}, new=${"%.2f".format(newMissedRate)}",
        )
    }

    private fun bedtimeDriftRecords(historyDays: Int, evaluatedDays: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        val totalDays = historyDays + evaluatedDays
        var dayStart = baseNow.minus(Duration.ofDays(totalDays.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        repeat(totalDays) { dayIndex ->
            val isEvaluatedDriftDay = dayIndex >= historyDays
            val nightStart = dayStart.plus(Duration.ofHours(19)).plus(Duration.ofMinutes(30))
            val nightEnd = nightStart.plus(Duration.ofHours(8))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = dayStart.plus(Duration.ofHours(10))
            val nap1End = nap1Start.plus(Duration.ofMinutes(75))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(15)).plus(Duration.ofMinutes(45))
            val nap2End = nap2Start.plus(Duration.ofMinutes(75))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            if (isEvaluatedDriftDay) {
                records[records.lastIndex] = records.last().copy(
                    startTime = dayStart.plus(Duration.ofHours(15)).plus(Duration.ofMinutes(45)),
                    endTime = dayStart.plus(Duration.ofHours(17)),
                )
            } else {
                records[records.lastIndex] = records.last().copy(
                    startTime = dayStart.plus(Duration.ofHours(16)).plus(Duration.ofMinutes(5)),
                    endTime = dayStart.plus(Duration.ofHours(17)).plus(Duration.ofMinutes(20)),
                )
            }

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records.filter { it.startTime.isBefore(baseNow) }.sortedBy { it.startTime }
    }

    private fun stableLateBedtimeRecords(days: Int): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        var dayStart = baseNow.minus(Duration.ofDays(days.toLong()))
            .atZone(zone).toLocalDate().atStartOfDay(zone).toInstant()
        repeat(days) {
            val nightStart = dayStart.plus(Duration.ofHours(21))
            val nightEnd = nightStart.plus(Duration.ofHours(7)).plus(Duration.ofMinutes(30))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            val nap1Start = dayStart.plus(Duration.ofHours(11))
            val nap1End = nap1Start.plus(Duration.ofMinutes(75))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            val nap2Start = dayStart.plus(Duration.ofHours(17)).plus(Duration.ofMinutes(15))
            val nap2End = nap2Start.plus(Duration.ofMinutes(75))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            dayStart = dayStart.plus(Duration.ofDays(1))
        }
        return records.filter { it.startTime.isBefore(baseNow) }.sortedBy { it.startTime }
    }

    private fun pairedBedtimeScores(records: List<SleepRecord>): List<Pair<AnchorScore, AnchorScore>> {
        val baselineHarness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                circadianFactorProvider = { _, _, _, _ -> SleepPredictionFactor.Neutral },
            )
        }
        val circadianHarness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            SleepWindowPredictor.predict(
                features = features,
                ageInWeeks = ageInWeeks,
                now = now,
                circadianFactorProvider = CircadianBiasFactor::adjustment,
            )
        }
        val baselineAnchors = baselineHarness.buildAnchors(records, emptyList(), baby).associateBy { it.wakeInstant }
        val circadianAnchors = circadianHarness.buildAnchors(records, emptyList(), baby)
            .filter { it.segmentKey.sleepType == SleepType.NIGHT_SLEEP }

        assertTrue(
            circadianAnchors.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Fixture must produce at least ${SleepPredictionTuning.EVAL_MIN_ANCHORS} bedtime anchors; got ${circadianAnchors.size}",
        )

        val newNullBedtimeCount = circadianAnchors.count { it.score == null }
        val baselineNullBedtimeCount = circadianAnchors.count { baselineAnchors[it.wakeInstant]?.score == null }
        assertTrue(
            newNullBedtimeCount <= baselineNullBedtimeCount,
            "Circadian predictor must not degrade Window availability; new nulls=$newNullBedtimeCount baseline=$baselineNullBedtimeCount",
        )

        val paired = circadianAnchors.mapNotNull { anchor ->
            val baselineAnchor = baselineAnchors[anchor.wakeInstant] ?: return@mapNotNull null
            val newScore = anchor.score ?: return@mapNotNull null
            val baselineScore = baselineAnchor.score ?: return@mapNotNull null
            newScore to baselineScore
        }

        assertTrue(
            paired.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Need at least ${SleepPredictionTuning.EVAL_MIN_ANCHORS} paired bedtime scores where both predictors returned Window; got ${paired.size}",
        )

        return paired
    }

    private fun bootstrapCiLowerBound(improvements: List<Double>, samples: Int = 1000): Double {
        val rng = Random(94)
        val means = (0 until samples).map {
            (0 until improvements.size).map { improvements[rng.nextInt(improvements.size)] }.average()
        }.sorted()
        return means[(0.05 * samples).toInt()]
    }
}
