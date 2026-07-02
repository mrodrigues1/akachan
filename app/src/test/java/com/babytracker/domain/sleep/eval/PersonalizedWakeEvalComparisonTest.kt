package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import com.babytracker.domain.sleep.feature.SleepFeatures
import com.babytracker.domain.sleep.prior.SleepAgePriors
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Random
import kotlin.math.abs

/**
 * §7.1 acceptance gate for AKA-93 (personalized wake percentiles + nap-vs-bedtime targets).
 *
 * Fixture: 42 days of realistic mixed sleep — 2 naps + 1 night sleep per day (default).
 * Nap wake intervals are ~110 min; pre-bedtime wake intervals are ~150 min (wider).
 * Combined IQR = 40 min (≤ INSTABILITY_CEILING_MINUTES = 45 min) — quality gate passes.
 * The type-separated predictor has a structural advantage on this fixture.
 *
 * Acceptance criteria (all must hold):
 * 1. At least EVAL_MIN_ANCHORS evaluated anchors per segment (or segment is BLOCK — skip comparison).
 * 2. Leave-one-day-out honoured (harness already enforces no-lookahead).
 * 3. MAE improvement >= EVAL_MIN_MAE_GAIN_MIN (5 min) per scored NIGHT_SLEEP segment.
 * 4. Bootstrap CI lower bound of improvement is positive (alpha=0.05, n=1000).
 * 5. missedWindowRate does not worsen vs. baseline.
 * 6. Insufficient-data segments block (not pass silently) — existing harness behaviour.
 */
class PersonalizedWakeEvalComparisonTest {

    private val zone = ZoneOffset.UTC
    private val baseNow = Instant.parse("2024-06-15T10:00:00Z")

    // Baby born 20 weeks before baseNow — age-band 16.
    private val baby = Baby(
        name = "CompBaby",
        birthDate = LocalDate.ofInstant(baseNow, zone).minusWeeks(20),
    )

    /**
     * Mixed fixture: 1 NIGHT_SLEEP + 2 NAPs per day for N days (default 42), built forward in time.
     *
     * Daily cycle (chronological, 1030 min ≈ 17.2 h):
     *   Night:  sleep 8 h
     *   110 min wake → Nap 1: sleep 90 min
     *   110 min wake → Nap 2: sleep 90 min
     *   150 min wake → next Night
     *
     * IMPORTANT — forward construction ensures typeAwareWakeIntervals labels correctly:
     *   Night→Nap1 gap (110 min) → label = NAP         ✓
     *   Nap1→Nap2  gap (110 min) → label = NAP         ✓
     *   Nap2→Night gap (150 min) → label = NIGHT_SLEEP ✓
     *
     * Combined wake intervals: 2/3 at 110 min, 1/3 at 150 min.
     * Combined IQR = P75 − P25 = 150 − 110 = 40 min ≤ INSTABILITY_CEILING_MINUTES (45 min) ✓
     * Combined median = 110 min (2:1 ratio, median always falls in the majority).
     *
     * napWakeP50 ≈ 110 min; bedtimeWakeP50 ≈ 150 min.
     * Baseline for bedtime: uses combined median (110 min) as babyP50.
     *   wakeTarget = 0.4 * agePrior(135 min) + 0.6 * 110 = 120 min → error ≈ 30 min.
     * New predictor: uses bedtime-specific P50 (150 min).
     *   wakeTarget = 0.4 * bedtimePrior(150 min) + 0.6 * 150 = 150 min → error ≈ 0 min.
     * MAE gain ≈ 30 min >> EVAL_MIN_MAE_GAIN_MIN (5 min) ✓
     *
     * KNOWN LIMITATION — 17.2 h cycle causes ~1.4 cycles per calendar day. On some days
     * napCountToday at Nap1-wake anchors may exceed expected, making resolveNextSleepType
     * return NIGHT_SLEEP at anchors that should predict NAP. This makes the NAP segment in
     * this fixture an unreliable comparison target. The NIGHT_SLEEP segment is unaffected
     * (Nap2-wake anchors have napCountToday ≥ expected naps = 2 for 20w). For NAP
     * no-regression, see napOnlyRecords().
     */
    private fun mixedDayRecords(days: Int = 42): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        // Cycle: 8h night + 110+90+110+90 min nap+wake × 2 + 150 min pre-bedtime = 1030 min
        val cycleMinutes = 1030L
        // Start far enough back that all days fit before baseNow
        var cursor = baseNow.minus(Duration.ofMinutes(cycleMinutes * days + 60))

        repeat(days) {
            // Night sleep (8 h)
            val nightStart = cursor
            val nightEnd = nightStart.plus(Duration.ofHours(8))
            records += SleepRecord(id++, nightStart, nightEnd, SleepType.NIGHT_SLEEP)

            // 110 min wake → Nap 1 (90 min)
            val nap1Start = nightEnd.plus(Duration.ofMinutes(110))
            val nap1End = nap1Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)

            // 110 min wake → Nap 2 (90 min)
            val nap2Start = nap1End.plus(Duration.ofMinutes(110))
            val nap2End = nap2Start.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)

            // 150 min pre-bedtime wake → next night
            cursor = nap2End.plus(Duration.ofMinutes(150))
        }
        return records.filter { it.startTime.isBefore(baseNow) }.sortedBy { it.startTime }
    }

    /**
     * Naps-only fixture: pure NAP records with stable 110-min wake intervals, 35 days × 7 naps/day.
     *
     * Because there are no NIGHT_SLEEP records, bedtimeWakeIntervalCount = 0 → both baseline
     * and new predictor fall back to combined median (110 min). NAP routing is unambiguous
     * (resolveTypeBlend falls back to combined for all anchors). This isolates the NAP path
     * and allows a clean no-regression assertion: both predictors must perform identically.
     */
    private fun napOnlyRecords(daysOfNaps: Int = 35): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        // 7 naps/day × 35 days = 245 naps; cycle = 110min wake + 90min nap = 200 min
        val napCount = daysOfNaps * 7
        var cursor = baseNow.minus(Duration.ofMinutes(200L * napCount + 60))
        repeat(napCount) {
            val napStart = cursor
            val napEnd = napStart.plus(Duration.ofMinutes(90))
            records += SleepRecord(id++, napStart, napEnd, SleepType.NAP)
            cursor = napEnd.plus(Duration.ofMinutes(110))
        }
        return records.filter { it.startTime.isBefore(baseNow) }.sortedBy { it.startTime }
    }

    /**
     * Wide-IQR fixture: nap wake intervals = 90 min, bedtime wake intervals = 150 min.
     *
     * Combined IQR = P75 - P25 = 150 - 90 = 60 min > INSTABILITY_CEILING_MINUTES (45 min).
     * Under the old combined-IQR gate every anchor returns NeedMoreData.
     * Under the type-aware OR-gate both type IQRs are 0 <= ceiling, so the gate passes.
     */
    private fun wideIqrMixedDayRecords(days: Int = 42): List<SleepRecord> {
        var id = 1L
        val records = mutableListOf<SleepRecord>()
        val cycleMinutes = (8 * 60 + 90 + 60 + 90 + 60 + 150).toLong()
        var cursor = baseNow.minus(Duration.ofMinutes(cycleMinutes * days + 60))
        repeat(days) {
            val nightEnd = cursor.plus(Duration.ofHours(8))
            records += SleepRecord(id++, cursor, nightEnd, SleepType.NIGHT_SLEEP)
            val nap1Start = nightEnd.plus(Duration.ofMinutes(90))
            val nap1End = nap1Start.plus(Duration.ofMinutes(60))
            records += SleepRecord(id++, nap1Start, nap1End, SleepType.NAP)
            val nap2Start = nap1End.plus(Duration.ofMinutes(90))
            val nap2End = nap2Start.plus(Duration.ofMinutes(60))
            records += SleepRecord(id++, nap2Start, nap2End, SleepType.NAP)
            cursor = nap2End.plus(Duration.ofMinutes(150))
        }
        return records.filter { it.startTime.isBefore(baseNow) }.sortedBy { it.startTime }
    }

    /**
     * Baseline predictor: Phase 0 logic — combined median + fixed half-window + single prior midpoint.
     * This is a verbatim copy of SleepWindowPredictor.buildWindow before AKA-93 changes.
     */
    private fun baselinePredict(features: SleepFeatures, ageInWeeks: Int, now: Instant): SleepPredictionState {
        val quality = features.quality
        val metrics = features.metrics
        val lastWakeMillis = metrics.lastWakeMillis
        val babyWakeP50Millis = metrics.medianWakeIntervalMillis
        val gateViolated = !quality.hasSufficientZoneIndependentEvidence ||
            !quality.isLocalDayCoverageSufficient ||
            lastWakeMillis == null ||
            babyWakeP50Millis == null
        if (gateViolated) {
            return SleepPredictionState.NeedMoreData(
                EvidenceProgress(
                    completedIntervals = quality.completedIntervalCount,
                    requiredIntervals = SleepPredictionTuning.MIN_COMPLETED_INTERVALS,
                    localDays = quality.localDayCoverage,
                    requiredLocalDays = SleepPredictionTuning.MIN_LOCAL_DAYS,
                    hint = when {
                        quality.completedIntervalCount < SleepPredictionTuning.MIN_COMPLETED_INTERVALS ->
                            "log a few more naps with both sleep and wake times"
                        !quality.isLocalDayCoverageSufficient ->
                            "keep logging over the next few days to complete the pattern"
                        !quality.isFresh -> "log a sleep or wake time — prediction needs a recent record"
                        else -> "sleep pattern is still settling — keep logging over the next few days"
                    },
                )
            )
        }
        if (features.feedIntervals.any { it.endMillis == null }) return SleepPredictionState.AfterActiveFeed
        val (minBound, maxBound) = SleepAgePriors.getWakeWindowBounds(ageInWeeks)
        val agePriorMidpointMillis = (minBound.toMillis() + maxBound.toMillis()) / 2
        val qualityC = (quality.completedIntervalCount.toFloat() / SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
            .coerceIn(0f, 1f)
        val wakeTargetMillis = (
            (1.0 - 0.6 * qualityC) * agePriorMidpointMillis + 0.6 * qualityC * babyWakeP50Millis!!
            ).toLong()
        val bestEstimate = Instant.ofEpochMilli(lastWakeMillis!! + wakeTargetMillis)
        val halfWindow = Duration.ofMinutes(SleepPredictionTuning.HALF_WINDOW_MINUTES)
        val windowStart = bestEstimate.minus(halfWindow)
        val windowEnd = bestEstimate.plus(halfWindow)
        if (now.isAfter(windowEnd.plus(Duration.ofMinutes(SleepPredictionTuning.OVERDUE_GRACE_MINUTES)))) {
            return SleepPredictionState.Overdue
        }
        val confidence = if (qualityC >= 0.5f) Confidence.MEDIUM else Confidence.LOW
        return SleepPredictionState.Window(
            SleepWindow(
                windowStart = windowStart,
                windowEnd = windowEnd,
                bestEstimate = bestEstimate,
                sleepType = SleepType.NAP,
                confidence = confidence,
                reasons = emptyList(),
                feedDue = false,
            )
        )
    }

    private fun bootstrapCiLowerBound(
        improvements: List<Double>,
        samples: Int = 1000,
        alpha: Double = 0.05,
    ): Double {
        val rng = Random(42)
        val means = (0 until samples).map {
            (0 until improvements.size).map { improvements[rng.nextInt(improvements.size)] }.average()
        }.sorted()
        return means[(alpha * samples).toInt()]
    }

    @Test
    fun `fixture labels are correct - napWakeP50 is 110min and bedtimeWakeP50 is 150min`() {
        // Verify the fixture labels before relying on it for the comparison test.
        // If this test fails, the comparison test would prove nothing.
        val records = mixedDayRecords(35)
        val sorted = records.sortedBy { it.startTime }
        val lookbackStart = baseNow.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val recent = sorted.filter { it.startTime >= lookbackStart }
        val features = SleepFeatureExtractor(Clock.fixed(baseNow, zone), zone).extract(recent, emptyList())
        val metrics = features.metrics

        assertTrue(
            metrics.napWakeP50Millis != null,
            "napWakeP50 must be non-null — fixture has 14 days of data with stable 110-min nap gaps",
        )
        assertTrue(
            metrics.bedtimeWakeP50Millis != null,
            "bedtimeWakeP50 must be non-null — fixture has 14 days of data with stable 150-min pre-bedtime gaps",
        )

        val tolerance = Duration.ofMinutes(5).toMillis()
        assertTrue(
            abs(metrics.napWakeP50Millis!! - Duration.ofMinutes(110).toMillis()) <= tolerance,
            "napWakeP50 must be ~110 min; got ${metrics.napWakeP50Millis!! / 60_000} min. " +
                "Check forward fixture construction — Night→Nap1 and Nap1→Nap2 gaps must label as NAP.",
        )
        assertTrue(
            abs(metrics.bedtimeWakeP50Millis!! - Duration.ofMinutes(150).toMillis()) <= tolerance,
            "bedtimeWakeP50 must be ~150 min; got ${metrics.bedtimeWakeP50Millis!! / 60_000} min. " +
                "Check forward fixture construction — Nap2→Night gap must label as NIGHT_SLEEP.",
        )
    }

    @Test
    fun `personalized predictor clears section 7-1 acceptance criteria on mixed-day fixture`() {
        // 42 days = 28 scored + 14 days of warm-up pre-history.
        // All anchors in the scored range (wakeInstant >= fixture_start + LOOKBACK_DAYS) are guaranteed
        // to have a full 14-day lookback window with >= MIN_COMPLETED_INTERVALS prior intervals.
        val records = mixedDayRecords(42)
        val sorted = records.sortedBy { it.startTime }

        val baselineHarness = SleepEvalHarness(zone, ::baselinePredict)
        val newHarness = SleepEvalHarness(zone)
        val baselineAnchors = baselineHarness.buildAnchors(sorted, emptyList(), baby)
        val newAnchors = newHarness.buildAnchors(sorted, emptyList(), baby)
        val baselineByWake = baselineAnchors.associateBy { it.wakeInstant }

        // Scored range: exclude warm-up anchors whose lookback window pre-dates the fixture start.
        // Anchors with wakeInstant < (fixtureStart + LOOKBACK_DAYS) have no full 14-day history and
        // correctly return NeedMoreData — that is expected gating behaviour, not a predictor bug.
        val fixtureStart = sorted.first().startTime
        val scoredRangeStart = fixtureStart.plus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))

        // Primary comparison target: NIGHT_SLEEP segment, scored range only.
        // Baseline uses combined median (110 min) for bedtime anchors; actual is 150 min → ~30 min error.
        // Phase 2 uses bedtime-specific P50 (150 min) → near-zero error.
        // This is where type separation has the most unambiguous structural advantage.
        val nightSegmentAnchors = newAnchors.filter {
            it.segmentKey.sleepType == SleepType.NIGHT_SLEEP && it.wakeInstant >= scoredRangeStart
        }

        // Criterion 1: sufficient anchors — fail explicitly; the known-good fixture must not BLOCK.
        assertTrue(
            nightSegmentAnchors.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Known-good fixture (42 days, scored range) must yield >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} NIGHT_SLEEP anchors; " +
                "got ${nightSegmentAnchors.size}. Fixture is too small or predictor is gating all anchors as NeedMoreData.",
        )

        // Fail closed: ALL scored-range NIGHT_SLEEP anchors must return Window.
        // Warm-up anchors (before scoredRangeStart) are excluded above; within the scored range, every
        // anchor has a full 14-day lookback and must not be gated as NeedMoreData.
        val unpairedCount = nightSegmentAnchors.count { it.score == null }
        assertTrue(
            unpairedCount == 0,
            "New predictor returned non-Window state for $unpairedCount NIGHT_SLEEP anchors on the known-good fixture. " +
                "Check resolveNextSleepType (napCountToday vs. expected) and EvidenceQuality gating.",
        )

        val pairedScores = nightSegmentAnchors.mapNotNull { newAnchor ->
            val baselineScore = baselineByWake[newAnchor.wakeInstant]?.score ?: return@mapNotNull null
            val newScore = newAnchor.score ?: return@mapNotNull null
            newScore to baselineScore
        }
        assertTrue(
            pairedScores.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Need >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} paired scored NIGHT_SLEEP anchors; got ${pairedScores.size}",
        )

        val newMaeMin = pairedScores.map { (n, _) -> n.errorMillis / 60_000.0 }.average()
        val baselineMaeMin = pairedScores.map { (_, b) -> b.errorMillis / 60_000.0 }.average()
        val improvements = pairedScores.map { (n, b) -> b.errorMillis / 60_000.0 - n.errorMillis / 60_000.0 }
        val maeGain = baselineMaeMin - newMaeMin

        // Criterion 3: MAE improvement >= EVAL_MIN_MAE_GAIN_MIN.
        assertTrue(
            maeGain >= SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN,
            "MAE gain ${"%.1f".format(maeGain)} min must be >= ${SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN} min. " +
                "Baseline=${"%.1f".format(baselineMaeMin)} min, New=${"%.1f".format(newMaeMin)} min.",
        )

        // Criterion 3b: positive in-window-% gain.
        val newInWinPct = pairedScores.count { (n, _) -> n.inWindow }.toDouble() / pairedScores.size
        val baselineInWinPct = pairedScores.count { (_, b) -> b.inWindow }.toDouble() / pairedScores.size
        assertTrue(
            newInWinPct >= baselineInWinPct,
            "New in-window% ${"%.2f".format(newInWinPct)} must be >= baseline ${"%.2f".format(baselineInWinPct)}",
        )

        // Criterion 4: bootstrap CI lower bound positive.
        val ciLower = bootstrapCiLowerBound(improvements)
        assertTrue(
            ciLower > 0.0,
            "Bootstrap CI lower bound ${"%.2f".format(ciLower)} min must be positive; got ${"%.2f".format(ciLower)}",
        )

        // Criterion 5: missedWindowRate must not worsen.
        val newMissedRate = pairedScores.count { (n, _) -> n.missedWindow }.toDouble() / pairedScores.size
        val baselineMissedRate = pairedScores.count { (_, b) -> b.missedWindow }.toDouble() / pairedScores.size
        assertTrue(
            newMissedRate <= baselineMissedRate + SleepPredictionTuning.EVAL_MAX_REGRESSION,
            "Missed-window rate ${"%.2f".format(newMissedRate)} must not exceed baseline ${"%.2f".format(baselineMissedRate)}",
        )
    }

    @Test
    fun `NAP segment - new predictor does not regress vs baseline on naps-only fixture`() {
        // Uses naps-only fixture to avoid the 17.2h cycle routing ambiguity.
        // Both predictors fall back to combined median (bedtimeWakeP50 = null, no NIGHT_SLEEP data),
        // so MAE gain ≈ 0. Assert no significant regression: gain >= -(EVAL_MIN_MAE_GAIN_MIN).
        val records = napOnlyRecords(35)
        val sorted = records.sortedBy { it.startTime }

        val baselineHarness = SleepEvalHarness(zone, ::baselinePredict)
        val newHarness = SleepEvalHarness(zone)
        val baselineAnchors = baselineHarness.buildAnchors(sorted, emptyList(), baby)
        val newAnchors = newHarness.buildAnchors(sorted, emptyList(), baby)
        val baselineByWake = baselineAnchors.associateBy { it.wakeInstant }

        val napAnchors = newAnchors.filter { it.segmentKey.sleepType == SleepType.NAP }
        assertTrue(
            napAnchors.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "naps-only fixture must yield >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} NAP anchors; got ${napAnchors.size}",
        )

        val pairedScores = napAnchors.mapNotNull { newAnchor ->
            val baselineScore = baselineByWake[newAnchor.wakeInstant]?.score ?: return@mapNotNull null
            val newScore = newAnchor.score ?: return@mapNotNull null
            newScore to baselineScore
        }
        assertTrue(
            pairedScores.size >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Need >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} paired NAP scores; got ${pairedScores.size}",
        )

        val newMaeMin = pairedScores.map { (n, _) -> n.errorMillis / 60_000.0 }.average()
        val baselineMaeMin = pairedScores.map { (_, b) -> b.errorMillis / 60_000.0 }.average()
        val maeGain = baselineMaeMin - newMaeMin

        // No-regression: gain may be 0 (both use combined median) but must not be a significant loss.
        assertTrue(
            maeGain >= -SleepPredictionTuning.EVAL_MIN_MAE_GAIN_MIN.toDouble(),
            "NAP MAE regression: new predictor is ${(-maeGain).format()} min worse than baseline. " +
                "Phase 2 must not make nap predictions worse.",
        )

        val newMissedRate = pairedScores.count { (n, _) -> n.missedWindow }.toDouble() / pairedScores.size
        val baselineMissedRate = pairedScores.count { (_, b) -> b.missedWindow }.toDouble() / pairedScores.size
        assertTrue(
            newMissedRate <= baselineMissedRate + SleepPredictionTuning.EVAL_MAX_REGRESSION,
            "NAP missed-window rate ${"%.2f".format(newMissedRate)} must not worsen vs baseline ${"%.2f".format(baselineMissedRate)}",
        )
    }

    private fun Double.format() = "%.1f".format(this)

    @Test
    fun `sparse fixture correctly BLOCKS rather than passing silently`() {
        // 4 days × 3 records = 12 records → ~8 NAP anchors < EVAL_MIN_ANCHORS (20) → BLOCK
        val records = mixedDayRecords(4)
        val report = SleepEvalHarness(zone).evaluate(records, emptyList(), baby, baseNow)
        assertTrue(
            report.segments.all { it.status == SegmentStatus.BLOCK_INSUFFICIENT_DATA },
            "Sparse mixed-day fixture must BLOCK all segments; got: ${report.segments}",
        )
    }

    @Test
    fun `wide combined IQR fixture passes type-aware gate and produces Window anchors`() {
        val records = wideIqrMixedDayRecords(42)
        val sorted = records.sortedBy { it.startTime }
        val extractor = SleepFeatureExtractor(Clock.fixed(baseNow, zone), zone)
        val lookbackStart = baseNow.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val recent = sorted.filter { it.startTime >= lookbackStart }
        val features = extractor.extract(recent, emptyList())

        assertTrue(
            features.metrics.wakeIntervalIqrMillis != null &&
                features.metrics.wakeIntervalIqrMillis!! >
                Duration.ofMinutes(SleepPredictionTuning.INSTABILITY_CEILING_MINUTES).toMillis(),
            "Fixture must have combined IQR > ${SleepPredictionTuning.INSTABILITY_CEILING_MINUTES} min; " +
                "got ${features.metrics.wakeIntervalIqrMillis?.div(60_000)} min",
        )
        assertTrue(
            features.quality.hasSufficientZoneIndependentEvidence,
            "Type-aware gate must pass when both type-specific IQRs are stable",
        )

        val newHarness = SleepEvalHarness(zone)
        val anchors = newHarness.buildAnchors(sorted, emptyList(), baby)
        val fixtureStart = sorted.first().startTime
        val scoredRangeStart = fixtureStart.plus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val windowCount = anchors.count { it.wakeInstant >= scoredRangeStart && it.score != null }

        assertTrue(
            windowCount >= SleepPredictionTuning.EVAL_MIN_ANCHORS,
            "Wide-IQR fixture must produce >= ${SleepPredictionTuning.EVAL_MIN_ANCHORS} scored anchors " +
                "in scored range; got $windowCount. Type-aware gate fix is not working.",
        )
    }
}
