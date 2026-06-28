package com.babytracker.domain.sleep.eval

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.domain.sleep.feature.SleepFeatureExtractor
import com.babytracker.domain.sleep.feature.SleepFeatures
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

class SleepEvalHarness(
    private val zoneId: ZoneId,
    private val predictor: (SleepFeatures, Int, Instant) -> SleepPredictionState = { features, ageInWeeks, now ->
        SleepWindowPredictor.predict(features, ageInWeeks, now)
    },
) {

    fun evaluate(
        records: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby,
        evaluatedAt: Instant,
    ): EvalReport {
        val sorted = records.filter { it.endTime != null }.sortedBy { it.startTime }
        val anchors = buildAnchors(sorted, feedSessions, baby)

        // Materialize the full grid: (anchor age bands ∪ baby's current age band) × all SleepTypes.
        // Including the baby's current age band ensures that even empty logs, single records, or
        // all-cue-led datasets emit BLOCK_INSUFFICIENT_DATA segments rather than an empty report —
        // an empty report would silently mask a failed coverage evaluation.
        val anchorAgeBands = anchors.map { it.segmentKey.ageBand }.toSet()
        val currentAgeBand = ageBandFor(
            ChronoUnit.WEEKS.between(baby.birthDate, evaluatedAt.atZone(zoneId).toLocalDate()).toInt()
        )
        val allKeys = (anchorAgeBands + currentAgeBand).flatMap { band ->
            SleepType.entries.map { type -> SegmentKey(band, type) }
        }.toSet()
        val grouped = anchors.groupBy { it.segmentKey }
        val segments = allKeys.map { key ->
            scoreSegment(key, grouped[key] ?: emptyList())
        }.sortedWith(compareBy({ it.key.ageBand }, { it.key.sleepType.name }))

        return EvalReport(
            algorithmVersion = SleepPredictionTuning.ALGORITHM_VERSION,
            evaluatedAt = evaluatedAt,
            segments = segments,
            totalAnchors = anchors.size,
            totalScored = anchors.count { it.score != null },
        )
    }

    // internal so tests can inspect per-anchor predictions directly to verify no-lookahead.
    internal fun buildAnchors(
        sorted: List<SleepRecord>,
        feedSessions: List<BreastfeedingSession>,
        baby: Baby,
    ): List<EvalAnchor> {
        val anchors = mutableListOf<EvalAnchor>()
        for (i in sorted.indices) {
            val nextRecord = sorted.getOrNull(i + 1) ?: continue

            val wakeInstant = sorted[i].endTime!!
            val ageInWeeks = ChronoUnit.WEEKS.between(
                baby.birthDate,
                wakeInstant.atZone(zoneId).toLocalDate(),
            ).toInt()
            if (ageInWeeks < SleepPredictionTuning.CUE_LED_MAX_AGE_WEEKS) continue

            val priorRecords = sorted.subList(0, i + 1)
            val lookbackStart = wakeInstant.minus(Duration.ofDays(SleepPredictionTuning.LOOKBACK_DAYS))
            // Point-in-time feed snapshot: a feed that started before wakeInstant but ends after it
            // must appear as active (endTime=null) — that is exactly what production sees at that moment.
            // Filtering only by startTime < wakeInstant would wrongly classify it as a completed feed
            // (or drop it), causing the harness to score a Window where production would return AfterActiveFeed.
            val priorFeeds = feedSessions.mapNotNull { session ->
                when {
                    session.startTime >= wakeInstant -> null
                    session.endTime == null || session.endTime > wakeInstant ->
                        session.copy(endTime = null)
                    else -> session
                }
            }

            val clockAtWake = Clock.fixed(wakeInstant, zoneId)
            val features = SleepFeatureExtractor(clockAtWake, zoneId)
                .extract(priorRecords.filter { it.startTime >= lookbackStart }, priorFeeds)

            val predictedState = predictor(features, ageInWeeks, wakeInstant)

            val segmentKey = SegmentKey(ageBandFor(ageInWeeks), nextRecord.sleepType)
            val score = (predictedState as? SleepPredictionState.Window)?.let { windowState ->
                val actualMillis = nextRecord.startTime.toEpochMilli()
                val errorMillis = abs(actualMillis - windowState.window.bestEstimate.toEpochMilli())
                val inWindow = actualMillis in
                    windowState.window.windowStart.toEpochMilli()..windowState.window.windowEnd.toEpochMilli()
                val hardMissThresholdMillis =
                    (SleepPredictionTuning.HALF_WINDOW_MINUTES + SleepPredictionTuning.OVERDUE_GRACE_MINUTES) * 60_000L
                AnchorScore(errorMillis, inWindow, errorMillis > hardMissThresholdMillis)
            }
            anchors += EvalAnchor(segmentKey, wakeInstant, predictedState, score)
        }
        return anchors
    }

    private fun scoreSegment(key: SegmentKey, anchors: List<EvalAnchor>): SegmentResult {
        if (anchors.size < SleepPredictionTuning.EVAL_MIN_ANCHORS) {
            return SegmentResult(
                key = key,
                anchorCount = anchors.size,
                scoredCount = 0,
                maeMinutes = null,
                inWindowPct = null,
                missedWindowRate = null,
                status = SegmentStatus.BLOCK_INSUFFICIENT_DATA,
                blockReason = "only ${anchors.size} anchors (min ${SleepPredictionTuning.EVAL_MIN_ANCHORS})",
            )
        }
        val scored = anchors.mapNotNull { it.score }
        if (scored.size < SleepPredictionTuning.EVAL_MIN_SCORED) {
            return SegmentResult(
                key = key,
                anchorCount = anchors.size,
                scoredCount = scored.size,
                maeMinutes = null,
                inWindowPct = null,
                missedWindowRate = null,
                status = SegmentStatus.BLOCK_INSUFFICIENT_DATA,
                blockReason = "only ${scored.size} Window-scored anchors (min ${SleepPredictionTuning.EVAL_MIN_SCORED}) — predictor returned non-Window state for ${anchors.size - scored.size} of ${anchors.size} wake events",
            )
        }
        return SegmentResult(
            key = key,
            anchorCount = anchors.size,
            scoredCount = scored.size,
            maeMinutes = scored.map { it.errorMillis / 60_000.0 }.average(),
            inWindowPct = scored.count { it.inWindow }.toDouble() / scored.size,
            missedWindowRate = scored.count { it.missedWindow }.toDouble() / scored.size,
            status = SegmentStatus.PASS,
            blockReason = null,
        )
    }
}

// internal so tests can call buildAnchors and inspect predictedState per anchor.
internal data class EvalAnchor(
    val segmentKey: SegmentKey,
    val wakeInstant: Instant,
    val predictedState: SleepPredictionState,
    val score: AnchorScore?,
)
