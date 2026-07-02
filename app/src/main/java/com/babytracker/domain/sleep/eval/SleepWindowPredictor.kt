package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepReason
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.sleep.median
import com.babytracker.domain.sleep.feature.BreastfeedInterval
import com.babytracker.domain.sleep.feature.EvidenceQuality
import com.babytracker.domain.sleep.feature.SleepFeatures
import com.babytracker.domain.sleep.feature.SleepMetrics
import com.babytracker.domain.sleep.prior.SleepAgePriors
import java.time.Duration
import java.time.Instant

object SleepWindowPredictor {

    fun predict(
        features: SleepFeatures,
        ageInWeeks: Int,
        now: Instant,
        circadianFactorProvider: CircadianFactorProvider = { _, _, _, _, _ -> SleepPredictionFactor.Neutral },
        sleepDebtFactorProvider: SleepDebtFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
        napBudgetFactorProvider: NapBudgetFactorProvider = { _, _, _ -> SleepPredictionFactor.Neutral },
    ): SleepPredictionState {
        val quality = features.quality
        if (!quality.hasSufficientZoneIndependentEvidence || !quality.isLocalDayCoverageSufficient) {
            return SleepPredictionState.NeedMoreData(buildProgress(quality))
        }
        if (features.feedIntervals.any { it.endMillis == null }) {
            return SleepPredictionState.AfterActiveFeed
        }
        return buildWindow(
            features, ageInWeeks, now,
            circadianFactorProvider,
            sleepDebtFactorProvider, napBudgetFactorProvider,
        )
    }

    private fun buildWindow(
        features: SleepFeatures,
        ageInWeeks: Int,
        now: Instant,
        circadianFactorProvider: CircadianFactorProvider,
        sleepDebtFactorProvider: SleepDebtFactorProvider,
        napBudgetFactorProvider: NapBudgetFactorProvider,
    ): SleepPredictionState {
        val quality = features.quality
        val metrics = features.metrics
        val lastWakeMillis = metrics.lastWakeMillis
            ?: return SleepPredictionState.NeedMoreData(buildProgress(quality))

        val nextType = resolveNextSleepType(metrics, ageInWeeks, features.currentMinuteOfDay)
        val (priorMidpointMillis, babyP50Millis, typeIntervalCount) =
            resolveTypeBlend(metrics, nextType, quality.completedIntervalCount, ageInWeeks)
                ?: return SleepPredictionState.NeedMoreData(buildProgress(quality))

        val qualityC = (typeIntervalCount.toFloat() / SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
            .coerceIn(0f, 1f)
        val personalizationWeight = SleepPredictionTuning.MAX_PERSONALIZATION_WEIGHT
        val wakeTargetMillis = (
            (1.0 - personalizationWeight * qualityC) * priorMidpointMillis +
                personalizationWeight * qualityC * babyP50Millis
            ).toLong()

        val bestEstimate = Instant.ofEpochMilli(lastWakeMillis + wakeTargetMillis)
        val halfWindowDuration = Duration.ofMillis(dynamicHalfWindowMillis(metrics, nextType))
        val candidateMinute = candidateMinuteOfDay(
            currentMinuteOfDay = features.currentMinuteOfDay,
            offsetFromNow = Duration.between(now, bestEstimate),
        )
        val circadianFactor = circadianFactorProvider(
            ageInWeeks,
            nextType,
            features.currentMinuteOfDay,
            candidateMinute,
            metrics.napCountToday,
        )
        val sleepDebtFactor = sleepDebtFactorProvider(
            metrics.sleepLast24hMillis,
            metrics.avgDailySleepMillis,
            ageInWeeks,
        )
        val napBudgetFactor = napBudgetFactorProvider(
            metrics.napCountToday,
            ageInWeeks,
            nextType,
        )
        val factors = listOf(circadianFactor, sleepDebtFactor, napBudgetFactor)
        val rawFactorShift = factors.fold(Duration.ZERO) { acc, f -> acc.plus(f.adjustment) }
        val totalFactorShift = decayFactorShift(rawFactorShift, qualityC)
        val adjustedBestEstimate = bestEstimate.plus(clampTotalShift(totalFactorShift))
        val windowStart = adjustedBestEstimate.minus(halfWindowDuration)
        val windowEnd = adjustedBestEstimate.plus(halfWindowDuration)
        // Staleness is judged on the post-factor window end only. Factors (circadian, sleep-debt,
        // nap-budget) are trusted to shift the displayed center, so a legitimate later shift must be
        // able to keep the window valid; gating on the pre-factor end would force Overdue for a window
        // the factors moved later. A negative (earlier) shift still surfaces Overdue here because it
        // pulls windowEnd earlier. The shift is clamped below the staleness gap, so a bounded later
        // shift can never revive a far-stale anchor.
        if (isStaleWindow(now, windowEnd)) {
            return SleepPredictionState.Overdue
        }

        val rawConfidence = when {
            qualityC >= SleepPredictionTuning.HIGH_CONFIDENCE_QUALITY_C_THRESHOLD &&
                quality.hasQualifiedTimezoneProvenance -> Confidence.HIGH
            qualityC >= 0.5f -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val confidence = if (features.hasActiveDisruption) rawConfidence.loweredByOne() else rawConfidence

        val disruptionReason = if (features.hasActiveDisruption) SleepReason.Disruption else null
        return SleepPredictionState.Window(
            SleepWindow(
                windowStart = windowStart,
                windowEnd = windowEnd,
                bestEstimate = adjustedBestEstimate,
                sleepType = nextType,
                confidence = confidence,
                reasons = buildReasons(qualityC, ageInWeeks, nextType, typeIntervalCount) +
                    listOfNotNull(disruptionReason) + factors.mapNotNull { it.reason },
                feedDue = computeFeedDue(features.feedIntervals, windowStart, windowEnd, now),
            )
        )
    }

    private fun resolveTypeBlend(
        metrics: SleepMetrics,
        nextType: SleepType,
        combinedIntervalCount: Int,
        ageInWeeks: Int,
    ): Triple<Long, Long, Int>? {
        // Defensive: the quality gate (≥5 completed wake intervals) guarantees
        // medianWakeIntervalMillis is non-null here; the null branch is unreachable in practice.
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

    internal fun resolveNextSleepType(
        metrics: SleepMetrics,
        ageInWeeks: Int,
        currentMinuteOfDay: Int? = null,
    ): SleepType = when (metrics.lastSleepType) {
        SleepType.NIGHT_SLEEP -> SleepType.NAP
        SleepType.NAP -> {
            val expected = SleepAgePriors.getScheduledNapCount(ageInWeeks)
            if (metrics.napCountToday >= expected) {
                SleepType.NIGHT_SLEEP
            } else if (isWithinLearnedBedtimeCutoff(currentMinuteOfDay, metrics.medianBedtimeMinuteOfDay, ageInWeeks)) {
                SleepType.NIGHT_SLEEP
            } else {
                SleepType.NAP
            }
        }
        null -> SleepType.NAP
    }

    private fun isWithinLearnedBedtimeCutoff(
        currentMinuteOfDay: Int?,
        bedtimeMinuteOfDay: Int?,
        ageInWeeks: Int,
    ): Boolean {
        if (currentMinuteOfDay == null || bedtimeMinuteOfDay == null) return false

        val thresholdMinutes = SleepAgePriors.getNapWakeWindowMidpoint(ageInWeeks).toMinutes().toInt()
        val minutesUntilBedtime = (bedtimeMinuteOfDay - currentMinuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY
        val minutesSinceBedtime = (currentMinuteOfDay - bedtimeMinuteOfDay + MINUTES_PER_DAY) % MINUTES_PER_DAY

        return minutesUntilBedtime <= thresholdMinutes || minutesSinceBedtime <= thresholdMinutes
    }

    private fun candidateMinuteOfDay(currentMinuteOfDay: Int?, offsetFromNow: Duration): Int? {
        if (currentMinuteOfDay == null) return null
        return Math.floorMod(currentMinuteOfDay + offsetFromNow.toMinutes().toInt(), MINUTES_PER_DAY)
    }

    // Each factor is capped individually, but their additive sum (worst case ~75 min) can exceed
    // the window's own width. Clamp the total so heuristic factors never shift the center more than
    // MAX_TOTAL_FACTOR_SHIFT_MINUTES, which stays below MAX_HALF_WINDOW_MINUTES.
    // Confidence-decay: population heuristics fade as the baby's own logged pattern takes over the
    // blend. factorWeight goes from 1.0 at qualityC = 0 (unknown baby — lean fully on the heuristics)
    // to FACTOR_FLOOR at qualityC = 1 (well-known baby — their own median already encodes the same
    // circadian / debt / nap structure, so the heuristics must carry less weight). Applied before the
    // total-shift clamp so the ceiling still holds on the decayed shift.
    private fun decayFactorShift(rawShift: Duration, qualityC: Float): Duration {
        val floor = SleepPredictionTuning.FACTOR_FLOOR
        val factorWeight = floor + (1.0 - floor) * (1.0 - qualityC)
        return Duration.ofMillis((rawShift.toMillis() * factorWeight).toLong())
    }

    private fun clampTotalShift(totalShift: Duration): Duration {
        val maxShift = Duration.ofMinutes(SleepPredictionTuning.MAX_TOTAL_FACTOR_SHIFT_MINUTES)
        return when {
            totalShift > maxShift -> maxShift
            totalShift < maxShift.negated() -> maxShift.negated()
            else -> totalShift
        }
    }

    private fun isStaleWindow(now: Instant, windowEnd: Instant): Boolean {
        val staleAfter = windowEnd.plus(Duration.ofMinutes(SleepPredictionTuning.OVERDUE_GRACE_MINUTES))
        return now.isAfter(staleAfter)
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
            // Type-specific quartiles need ≥4 intervals, but the type P50 center is trusted from 3
            // (MIN_TYPE_INTERVALS). Bridge that gap with the combined-history IQR — guaranteed present
            // by the ≥5 completed-interval quality gate — so a confident center isn't paired with a
            // MIN-floored window.
            metrics.wakeIntervalIqrMillis != null -> metrics.wakeIntervalIqrMillis / 2
            else -> minMillis
        }
        return halfWidthMillis.coerceIn(minMillis, maxMillis)
    }

    private fun buildProgress(quality: EvidenceQuality) = EvidenceProgress(
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

    private fun buildReasons(
        qualityC: Float,
        ageInWeeks: Int,
        nextType: SleepType,
        typeIntervalCount: Int,
    ): List<SleepReason> {
        val pct = (qualityC * 100).toInt()
        val (minBound, maxBound) = SleepAgePriors.getWakeWindowBounds(ageInWeeks)
        return listOf(
            if (qualityC >= 1f) {
                SleepReason.FullyPersonalized(nextType)
            } else {
                SleepReason.Blended(pct, nextType)
            },
            SleepReason.TypicalWakeWindow(ageInWeeks, minBound.toMinutes(), maxBound.toMinutes()),
            if (typeIntervalCount >= SleepPredictionTuning.MIN_TYPE_INTERVALS) {
                SleepReason.TypeSpecificPattern(nextType, typeIntervalCount)
            } else {
                SleepReason.CombinedHistory(nextType)
            },
        )
    }

    private fun computeFeedDue(
        feedIntervals: List<BreastfeedInterval>,
        windowStart: Instant,
        windowEnd: Instant,
        now: Instant,
    ): Boolean {
        val freshnessMillis = Duration.ofHours(SleepPredictionTuning.FRESHNESS_HORIZON_HOURS).toMillis()
        val nowMillis = now.toEpochMilli()
        val recent = feedIntervals
            .filter { it.endMillis != null && (nowMillis - it.startMillis) <= freshnessMillis }
            .sortedByDescending { it.startMillis }
        val lastFeed = recent.firstOrNull() ?: return false
        val intervals = recent.zipWithNext { a, b -> a.startMillis - b.startMillis }.filter { it > 0 }
        val medianIntervalMillis = median(intervals) ?: return false
        val predictedNextFeed = Instant.ofEpochMilli(lastFeed.startMillis + medianIntervalMillis)
        val toleranceMillis = Duration.ofMinutes(30).toMillis()
        val predictedMillis = predictedNextFeed.toEpochMilli()
        val windowStartMillis = windowStart.toEpochMilli()
        val windowEndMillis = windowEnd.toEpochMilli()
        return predictedMillis in (windowStartMillis - toleranceMillis)..(windowEndMillis + toleranceMillis)
    }

    private const val MINUTES_PER_DAY = 1_440
}

private fun Confidence.loweredByOne(): Confidence = when (this) {
    Confidence.HIGH -> Confidence.MEDIUM
    Confidence.MEDIUM -> Confidence.LOW
    Confidence.LOW -> Confidence.LOW
}
