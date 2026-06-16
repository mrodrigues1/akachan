package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.sleep.feature.SleepFeatures
import com.babytracker.domain.sleep.feature.SleepMetrics
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Knobs swept by Issues 2 ([AKA-154], blend) and 3 ([AKA-155], factor decay). The defaults reproduce
 * the *current* production algorithm exactly — guarded by the parity test in
 * [SleepPredictionCohortsTest]. Candidate grids vary these to search for a configuration that reduces
 * the short-wake late bias without regressing the typical / long-wake cohorts.
 *
 * @param maxPersonalizationWeight cap on how much the baby's own P50 displaces the age prior
 *   (`SleepPredictionTuning.MAX_PERSONALIZATION_WEIGHT`).
 * @param fullPersonalizationIntervals type-specific intervals at which `qualityC` saturates to 1.0
 *   (`SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS`).
 * @param factorFloor floor of the confidence-decay weight `factorWeight(c) = floor + (1-floor)(1-c)`
 *   applied to the summed heuristic-factor shift. `1.0` = no decay (current behaviour); lower values
 *   fade the factors as the baby becomes well known.
 * @param maxTotalFactorShiftMinutes ceiling on the summed factor shift after decay
 *   (`SleepPredictionTuning.MAX_TOTAL_FACTOR_SHIFT_MINUTES`).
 */
data class SweepConfig(
    val maxPersonalizationWeight: Double = SleepPredictionTuning.MAX_PERSONALIZATION_WEIGHT,
    val fullPersonalizationIntervals: Int = SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS,
    val factorFloor: Double = 1.0,
    val maxTotalFactorShiftMinutes: Long = SleepPredictionTuning.MAX_TOTAL_FACTOR_SHIFT_MINUTES,
) {
    fun label(): String =
        "W=$maxPersonalizationWeight,full=$fullPersonalizationIntervals," +
            "floor=$factorFloor,cap=$maxTotalFactorShiftMinutes"
}

/**
 * Configurable mirror of [SleepWindowPredictor.buildWindow]. Reimplements only the scoring-relevant
 * math (gate → blend → factor application → window/staleness) so the sweep can vary [SweepConfig]
 * knobs that production reads from compile-time constants. Reasons / confidence / feed-prompt are
 * omitted because they do not affect MAE / in-window / missed-rate scoring.
 *
 * Reuses production [SleepWindowPredictor.resolveNextSleepType] and the real public factor functions.
 * Kept faithful to production by [SleepPredictionCohortsTest]'s default-config parity assertion — if
 * production's window math changes, that test fails and signals this mirror needs updating.
 */
fun sweepPredict(
    config: SweepConfig,
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadian: CircadianFactorProvider = CircadianBiasFactor::adjustment,
    sleepDebt: SleepDebtFactorProvider = SleepDebtFactor::adjustment,
    napBudget: NapBudgetFactorProvider = NapBudgetFactor::adjustment,
): SleepPredictionState {
    val quality = features.quality
    if (!quality.hasSufficientZoneIndependentEvidence || !quality.isLocalDayCoverageSufficient) {
        return needMoreData(quality.completedIntervalCount, quality.localDayCoverage)
    }
    if (features.feedIntervals.any { it.endMillis == null }) return SleepPredictionState.AfterActiveFeed
    return sweepBuildWindow(config, features, ageInWeeks, now, circadian, sleepDebt, napBudget)
}

private fun sweepBuildWindow(
    config: SweepConfig,
    features: SleepFeatures,
    ageInWeeks: Int,
    now: Instant,
    circadian: CircadianFactorProvider,
    sleepDebt: SleepDebtFactorProvider,
    napBudget: NapBudgetFactorProvider,
): SleepPredictionState {
    val quality = features.quality
    val metrics = features.metrics
    val lastWakeMillis = metrics.lastWakeMillis
        ?: return needMoreData(quality.completedIntervalCount, quality.localDayCoverage)

    val nextType = SleepWindowPredictor.resolveNextSleepType(metrics, ageInWeeks, features.currentMinuteOfDay)
    val blend = resolveTypeBlend(metrics, nextType, quality.completedIntervalCount, ageInWeeks)
        ?: return needMoreData(quality.completedIntervalCount, quality.localDayCoverage)
    val (priorMidpointMillis, babyP50Millis, typeIntervalCount) = blend

    val qualityC = (typeIntervalCount.toFloat() / config.fullPersonalizationIntervals).coerceIn(0f, 1f)
    val wakeTargetMillis = (
        (1.0 - config.maxPersonalizationWeight * qualityC) * priorMidpointMillis +
            config.maxPersonalizationWeight * qualityC * babyP50Millis
        ).toLong()
    val bestEstimate = Instant.ofEpochMilli(lastWakeMillis + wakeTargetMillis)

    val halfWindow = Duration.ofMillis(dynamicHalfWindowMillis(metrics, nextType))
    val candidateMinute = candidateMinuteOfDay(features.currentMinuteOfDay, Duration.between(now, bestEstimate))
    val factors = listOf(
        circadian(ageInWeeks, nextType, features.currentMinuteOfDay, candidateMinute, metrics.napCountToday),
        sleepDebt(metrics.sleepLast24hMillis, metrics.avgDailySleepMillis, ageInWeeks),
        napBudget(metrics.napCountToday, ageInWeeks, nextType),
    )
    val rawShift = factors.fold(Duration.ZERO) { acc, f -> acc.plus(f.adjustment) }
    val factorWeight = config.factorFloor + (1.0 - config.factorFloor) * (1.0 - qualityC)
    val scaledShift = Duration.ofMillis((rawShift.toMillis() * factorWeight).toLong())
    val totalShift = clampTotalShift(scaledShift, config.maxTotalFactorShiftMinutes)
    val adjusted = bestEstimate.plus(totalShift)

    val windowStart = adjusted.minus(halfWindow)
    val windowEnd = adjusted.plus(halfWindow)
    val staleAfter = windowEnd.plus(Duration.ofMinutes(SleepPredictionTuning.OVERDUE_GRACE_MINUTES))
    if (now.isAfter(staleAfter)) return SleepPredictionState.Overdue

    return SleepPredictionState.Window(
        SleepWindow(
            windowStart = windowStart,
            windowEnd = windowEnd,
            bestEstimate = adjusted,
            confidence = Confidence.MEDIUM,
            reasons = emptyList(),
            feedPrompt = null,
            safetyPrompt = "sweep",
        ),
    )
}

// --- buildWindow helpers mirrored from SleepWindowPredictor (private there). Keep in sync; ---
// --- the default-config parity test fails if these diverge from production scoring. ---

private fun resolveTypeBlend(
    metrics: SleepMetrics,
    nextType: SleepType,
    combinedIntervalCount: Int,
    ageInWeeks: Int,
): Triple<Long, Long, Int>? {
    val combinedP50 = metrics.medianWakeIntervalMillis ?: return null
    return when (nextType) {
        SleepType.NAP -> {
            val prior = SleepAgePriors.getNapWakeWindowMidpoint(ageInWeeks)
            val (p50, count) = if (metrics.napWakeIntervalCount >= SleepPredictionTuning.MIN_TYPE_INTERVALS &&
                metrics.napWakeP50Millis != null
            ) {
                metrics.napWakeP50Millis to metrics.napWakeIntervalCount
            } else {
                combinedP50 to combinedIntervalCount
            }
            Triple(prior.toMillis(), p50, count)
        }
        SleepType.NIGHT_SLEEP -> {
            val prior = SleepAgePriors.getPreBedtimeWakeWindowMidpoint(ageInWeeks)
            val (p50, count) = if (metrics.bedtimeWakeIntervalCount >= SleepPredictionTuning.MIN_TYPE_INTERVALS &&
                metrics.bedtimeWakeP50Millis != null
            ) {
                metrics.bedtimeWakeP50Millis to metrics.bedtimeWakeIntervalCount
            } else {
                combinedP50 to combinedIntervalCount
            }
            Triple(prior.toMillis(), p50, count)
        }
    }
}

private fun dynamicHalfWindowMillis(metrics: SleepMetrics, nextType: SleepType): Long {
    val (p25, p75) = when (nextType) {
        SleepType.NAP -> metrics.napWakeP25Millis to metrics.napWakeP75Millis
        SleepType.NIGHT_SLEEP -> metrics.bedtimeWakeP25Millis to metrics.bedtimeWakeP75Millis
    }
    val minMillis = Duration.ofMinutes(SleepPredictionTuning.MIN_HALF_WINDOW_MINUTES).toMillis()
    val maxMillis = Duration.ofMinutes(SleepPredictionTuning.MAX_HALF_WINDOW_MINUTES).toMillis()
    val halfWidthMillis = when {
        p25 != null && p75 != null -> (p75 - p25) / 2
        metrics.wakeIntervalIqrMillis != null -> metrics.wakeIntervalIqrMillis / 2
        else -> minMillis
    }
    return halfWidthMillis.coerceIn(minMillis, maxMillis)
}

private fun candidateMinuteOfDay(currentMinuteOfDay: Int?, offsetFromNow: Duration): Int? {
    if (currentMinuteOfDay == null) return null
    return Math.floorMod(currentMinuteOfDay + offsetFromNow.toMinutes().toInt(), 1_440)
}

private fun clampTotalShift(totalShift: Duration, maxShiftMinutes: Long): Duration {
    val maxShift = Duration.ofMinutes(maxShiftMinutes)
    return when {
        totalShift > maxShift -> maxShift
        totalShift < maxShift.negated() -> maxShift.negated()
        else -> totalShift
    }
}

private fun needMoreData(completedIntervals: Int, localDays: Int) = SleepPredictionState.NeedMoreData(
    EvidenceProgress(
        completedIntervals = completedIntervals,
        requiredIntervals = SleepPredictionTuning.MIN_COMPLETED_INTERVALS,
        localDays = localDays,
        requiredLocalDays = SleepPredictionTuning.MIN_LOCAL_DAYS,
        hint = "sweep — insufficient data",
    ),
)

/**
 * Runs candidate [SweepConfig]s over the synthetic cohorts and reports per-cohort, per-segment
 * metrics — the search tool for Issues 2 & 3. Fully deterministic: per-anchor fixed clocks (via
 * [SleepEvalHarness.buildAnchors]), no RNG, no lookahead.
 */
class ParametricSweepHarness(private val zone: ZoneId) {

    data class SegmentMetrics(
        val segment: SegmentKey,
        val anchorCount: Int,
        val maeMinutes: Double?,
        /** Signed: predicted onset − actual onset, in minutes. Positive = window too far ahead (late). */
        val signedBiasMinutes: Double?,
        val inWindowPct: Double?,
        val missedRate: Double?,
    )

    data class CohortResult(
        val cohortLabel: String,
        val segments: List<SegmentMetrics>,
    ) {
        fun segment(type: SleepType): SegmentMetrics? = segments.firstOrNull { it.segment.sleepType == type }
    }

    data class CandidateResult(
        val config: SweepConfig,
        val cohorts: List<CohortResult>,
    ) {
        fun cohort(label: String): CohortResult? = cohorts.firstOrNull { it.cohortLabel == label }
    }

    fun run(
        cohorts: List<SleepPredictionCohorts.Cohort>,
        configs: List<SweepConfig>,
    ): List<CandidateResult> = configs.map { config ->
        CandidateResult(
            config = config,
            cohorts = cohorts.map { cohort -> evaluate(config, cohort) },
        )
    }

    private fun evaluate(config: SweepConfig, cohort: SleepPredictionCohorts.Cohort): CohortResult {
        val harness = SleepEvalHarness(zone) { features, ageInWeeks, now ->
            sweepPredict(config, features, ageInWeeks, now)
        }
        val anchors = harness.buildAnchors(cohort.records, emptyList(), cohort.baby)
            .filter { cohort.isScoredWake(it.wakeInstant) }

        val segments = anchors
            .groupBy { it.segmentKey }
            .map { (key, segmentAnchors) -> metricsFor(key, segmentAnchors, cohort) }
            .sortedBy { it.segment.sleepType.name }
        return CohortResult(cohort.params.label, segments)
    }

    private fun metricsFor(
        key: SegmentKey,
        anchors: List<EvalAnchor>,
        cohort: SleepPredictionCohorts.Cohort,
    ): SegmentMetrics {
        val scored = anchors.mapNotNull { it.score }
        if (scored.isEmpty()) {
            return SegmentMetrics(key, anchors.size, null, null, null, null)
        }
        val signedBiases = anchors.mapNotNull { anchor ->
            val window = (anchor.predictedState as? SleepPredictionState.Window)?.window ?: return@mapNotNull null
            val actual = cohort.actualOnsetByWake[anchor.wakeInstant] ?: return@mapNotNull null
            (window.bestEstimate.toEpochMilli() - actual.toEpochMilli()) / 60_000.0
        }
        return SegmentMetrics(
            segment = key,
            anchorCount = anchors.size,
            maeMinutes = scored.map { it.errorMillis / 60_000.0 }.average(),
            signedBiasMinutes = signedBiases.takeIf { it.isNotEmpty() }?.average(),
            inWindowPct = scored.count { it.inWindow }.toDouble() / scored.size,
            missedRate = scored.count { it.missedWindow }.toDouble() / scored.size,
        )
    }
}
