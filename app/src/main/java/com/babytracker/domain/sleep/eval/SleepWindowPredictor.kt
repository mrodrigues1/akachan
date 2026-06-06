package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.model.SleepWindow
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
        timeOfDayFactorProvider: TimeOfDayFactorProvider = { _, _, _, _ -> SleepPredictionFactor.Disabled },
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
            circadianFactorProvider, timeOfDayFactorProvider,
            sleepDebtFactorProvider, napBudgetFactorProvider,
        )
    }

    private fun buildWindow(
        features: SleepFeatures,
        ageInWeeks: Int,
        now: Instant,
        circadianFactorProvider: CircadianFactorProvider,
        timeOfDayFactorProvider: TimeOfDayFactorProvider,
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
        val wakeTargetMillis = (
            (1.0 - 0.6 * qualityC) * priorMidpointMillis +
                0.6 * qualityC * babyP50Millis
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
        val timeOfDayFactor = timeOfDayFactorProvider(metrics, nextType, candidateMinute, false)
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
        val adjustedBestEstimate = bestEstimate
            .plus(circadianFactor.adjustment)
            .plus(timeOfDayFactor.adjustment)
            .plus(sleepDebtFactor.adjustment)
            .plus(napBudgetFactor.adjustment)
        val windowStart = adjustedBestEstimate.minus(halfWindowDuration)
        val windowEnd = adjustedBestEstimate.plus(halfWindowDuration)
        // Stale check uses the pre-factor end (so a positive factor cannot revive a stale anchor)
        // and the post-factor end (so a negative factor cannot produce a stale final window).
        if (isStaleWindow(now, bestEstimate.plus(halfWindowDuration)) || isStaleWindow(now, windowEnd)) {
            return SleepPredictionState.Overdue
        }

        val rawConfidence = when {
            qualityC >= SleepPredictionTuning.HIGH_CONFIDENCE_QUALITY_C_THRESHOLD &&
                quality.hasQualifiedTimezoneProvenance -> Confidence.HIGH
            qualityC >= 0.5f -> Confidence.MEDIUM
            else -> Confidence.LOW
        }
        val confidence = if (features.hasActiveDisruption) rawConfidence.loweredByOne() else rawConfidence

        val disruptionReason = if (features.hasActiveDisruption) {
            "A sick, teething, or travel event in the last 48 h has reduced prediction confidence"
        } else {
            null
        }
        return SleepPredictionState.Window(
            SleepWindow(
                windowStart = windowStart,
                windowEnd = windowEnd,
                bestEstimate = adjustedBestEstimate,
                confidence = confidence,
                reasons = buildReasons(qualityC, ageInWeeks, nextType, typeIntervalCount) +
                    listOfNotNull(disruptionReason, circadianFactor.reason, timeOfDayFactor.reason, sleepDebtFactor.reason, napBudgetFactor.reason),
                feedPrompt = computeFeedPrompt(features.feedIntervals, windowStart, windowEnd, now),
                safetyPrompt = "Always follow your baby's sleep cues — windows are estimates, not schedules.",
            )
        )
    }

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
        return if (p25 != null && p75 != null) {
            ((p75 - p25) / 2).coerceIn(minMillis, maxMillis)
        } else {
            minMillis
        }
    }

    private fun buildProgress(quality: EvidenceQuality) = EvidenceProgress(
        completedIntervals = quality.completedIntervalCount,
        requiredIntervals = SleepPredictionTuning.MIN_COMPLETED_INTERVALS,
        localDays = quality.localDayCoverage,
        requiredLocalDays = SleepPredictionTuning.MIN_LOCAL_DAYS,
        hint = "log a few more naps with both sleep and wake times",
    )

    private fun buildReasons(
        qualityC: Float,
        ageInWeeks: Int,
        nextType: SleepType,
        typeIntervalCount: Int,
    ): List<String> {
        val pct = (qualityC * 100).toInt()
        val (minBound, maxBound) = SleepAgePriors.getWakeWindowBounds(ageInWeeks)
        val typeLabel = if (nextType == SleepType.NIGHT_SLEEP) "bedtime" else "nap"
        return listOf(
            if (qualityC >= 1f) {
                "Fully personalized from your baby's $typeLabel history"
            } else {
                "Blended from age-based expectations ($pct% personalized from your baby's $typeLabel history)"
            },
            "Typical wake window for ${ageInWeeks}w: ${minBound.toMinutes()}–${maxBound.toMinutes()} min",
            if (typeIntervalCount >= SleepPredictionTuning.MIN_TYPE_INTERVALS) {
                "Using your baby's ${typeLabel}-specific wake patterns ($typeIntervalCount intervals)"
            } else {
                "Using combined wake history (not enough $typeLabel-specific data yet)"
            },
        )
    }

    private fun computeFeedPrompt(
        feedIntervals: List<BreastfeedInterval>,
        windowStart: Instant,
        windowEnd: Instant,
        now: Instant,
    ): String? {
        val freshnessMillis = Duration.ofHours(SleepPredictionTuning.FRESHNESS_HORIZON_HOURS).toMillis()
        val nowMillis = now.toEpochMilli()
        val recent = feedIntervals
            .filter { it.endMillis != null && (nowMillis - it.startMillis) <= freshnessMillis }
            .sortedByDescending { it.startMillis }
        val lastFeed = recent.firstOrNull() ?: return null
        val intervals = recent.zipWithNext { a, b -> a.startMillis - b.startMillis }.filter { it > 0 }
        if (intervals.isEmpty()) return null
        val avgIntervalMillis = intervals.average().toLong()
        val predictedNextFeed = Instant.ofEpochMilli(lastFeed.startMillis + avgIntervalMillis)
        val toleranceMillis = Duration.ofMinutes(30).toMillis()
        val predictedMillis = predictedNextFeed.toEpochMilli()
        val windowStartMillis = windowStart.toEpochMilli()
        val windowEndMillis = windowEnd.toEpochMilli()
        return if (predictedMillis in (windowStartMillis - toleranceMillis)..(windowEndMillis + toleranceMillis)) {
            "a breastfeed may be due near this window — offer a feed first if hunger cues appear"
        } else {
            null
        }
    }

    private const val MINUTES_PER_DAY = 1_440
}

private fun Confidence.loweredByOne(): Confidence = when (this) {
    Confidence.HIGH -> Confidence.MEDIUM
    Confidence.MEDIUM -> Confidence.LOW
    Confidence.LOW -> Confidence.LOW
}
