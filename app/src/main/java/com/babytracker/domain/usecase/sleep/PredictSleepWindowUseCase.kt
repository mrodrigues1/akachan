package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.Confidence
import com.babytracker.domain.model.EvidenceProgress
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.BreastfeedingRepository
import com.babytracker.domain.repository.SleepRepository
import com.babytracker.domain.sleep.feature.BreastfeedInterval
import com.babytracker.domain.sleep.feature.EvidenceQuality
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import com.babytracker.domain.sleep.feature.SleepFeatures
import com.babytracker.domain.sleep.prior.SleepAgePriors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject

class PredictSleepWindowUseCase @Inject constructor(
    private val sleepRepository: SleepRepository,
    private val breastfeedingRepository: BreastfeedingRepository,
    private val babyRepository: BabyRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {

    // flow { emitAll(combine(...)) } defers repository calls into the flow body so .catch intercepts
    // synchronous throws from repository methods (before any emissions) as Unavailable states.
    operator fun invoke(): Flow<SleepPredictionState> = flow {
        emitAll(
            combine(
                sleepRepository.getAllRecords(),
                breastfeedingRepository.getAllSessions(),
                babyRepository.getBabyProfile(),
            ) { sleepRecords, feedSessions, baby ->
                predict(sleepRecords, feedSessions, baby)
            }
        )
    }.catch { e ->
        emit(SleepPredictionState.Unavailable(e.message ?: "prediction error"))
    }

    private fun predict(
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby?,
    ): SleepPredictionState {
        val now = Instant.now(clock)
        // Mirror SleepFeatureExtractor.isPossibleAt() so a stale open record (> MAX_OPEN_SLEEP_AGE_HOURS)
        // cannot permanently suppress predictions.
        val maxOpenSleepAgeMillis = Duration.ofHours(SleepPredictionTuning.MAX_OPEN_SLEEP_AGE_HOURS).toMillis()
        val hasActiveSleep = sleepRecords.any { record ->
            record.endTime == null && (now.toEpochMilli() - record.startTime.toEpochMilli()) <= maxOpenSleepAgeMillis
        }
        if (hasActiveSleep) return SleepPredictionState.CurrentlySleeping
        baby ?: return SleepPredictionState.Unavailable("no baby profile")
        return predictForBaby(baby, sleepRecords, feedSessions, now)
    }

    private fun predictForBaby(
        baby: Baby,
        sleepRecords: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        now: Instant,
    ): SleepPredictionState {
        // Baby.ageInWeeks uses LocalDate.now() (no clock injection) — compute from injected clock
        // here so tests with a fixed clock produce deterministic age-gate results.
        val today = now.atZone(zoneId).toLocalDate()
        val ageInWeeks = ChronoUnit.WEEKS.between(baby.birthDate, today).toInt()

        if (ageInWeeks < SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS) return SleepPredictionState.CueLed

        val lookbackStart = now.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
        val features = SleepFeatureExtractor(clock, zoneId)
            .extract(sleepRecords.filter { it.startTime >= lookbackStart }, feedSessions)

        if (!features.quality.hasSufficientZoneIndependentEvidence || !features.quality.isLocalDayCoverageSufficient) {
            return SleepPredictionState.NeedMoreData(buildProgress(features.quality))
        }

        // Use validated feed intervals (already clock-bounded by SleepFeatureExtractor) so a
        // stale open feed older than MAX_OPEN_FEED_AGE_HOURS cannot suppress predictions forever.
        if (features.feedIntervals.any { it.endMillis == null }) return SleepPredictionState.AfterActiveFeed

        return computeWindow(features, ageInWeeks, now)
    }

    private fun computeWindow(
        features: SleepFeatures,
        ageInWeeks: Int,
        now: Instant,
    ): SleepPredictionState {
        val quality = features.quality
        val metrics = features.metrics

        val lastWakeMillis = metrics.lastWakeMillis
            ?: return SleepPredictionState.NeedMoreData(buildProgress(quality))
        val babyWakeP50Millis = metrics.medianWakeIntervalMillis
            ?: return SleepPredictionState.NeedMoreData(buildProgress(quality))

        val (minBound, maxBound) = SleepAgePriors.getWakeWindowBounds(ageInWeeks)
        val agePriorMidpointMillis = (minBound.toMillis() + maxBound.toMillis()) / 2

        val qualityC = (quality.completedIntervalCount.toFloat() / SleepPredictionTuning.FULL_PERSONALIZATION_INTERVALS)
            .coerceIn(0f, 1f)
        val wakeTargetMillis = (
            (1.0 - 0.6 * qualityC) * agePriorMidpointMillis +
                0.6 * qualityC * babyWakeP50Millis
            ).toLong()

        val bestEstimate = Instant.ofEpochMilli(lastWakeMillis + wakeTargetMillis)
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
                confidence = confidence,
                reasons = buildReasons(qualityC, ageInWeeks),
                feedPrompt = computeFeedPrompt(features.feedIntervals, windowStart, windowEnd, now),
                safetyPrompt = "Always follow your baby's sleep cues — windows are estimates, not schedules.",
            )
        )
    }

    private fun buildProgress(quality: EvidenceQuality) = EvidenceProgress(
        completedIntervals = quality.completedIntervalCount,
        requiredIntervals = SleepPredictionTuning.MIN_COMPLETED_INTERVALS,
        localDays = quality.localDayCoverage,
        requiredLocalDays = SleepPredictionTuning.MIN_LOCAL_DAYS,
        hint = "log a few more naps with both sleep and wake times",
    )

    private fun buildReasons(qualityC: Float, ageInWeeks: Int): List<String> {
        val pct = (qualityC * 100).toInt()
        val (minBound, maxBound) = SleepAgePriors.getWakeWindowBounds(ageInWeeks)
        return listOf(
            if (qualityC >= 1f) {
                "Fully personalized from your baby's wake history"
            } else {
                "Blended from age-based expectations ($pct% personalized from your baby's history)"
            },
            "Typical wake window for ${ageInWeeks}w: ${minBound.toMinutes()}–${maxBound.toMinutes()} min",
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
}
