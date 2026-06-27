package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.PredictionTuning
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** One gap between two consecutive sessions: the older/newer start instants and the minutes between. */
internal data class IntervalSample(
    val endpointA: Instant,
    val endpointB: Instant,
    val minutes: Int,
)

/**
 * Consecutive-session gaps worth sampling for feed prediction: sort [sessions] newest-first, pair
 * adjacent sessions, drop gaps longer than [PredictionTuning.INTERVAL_MAX_MINUTES] and any whose
 * endpoint falls in quiet hours, then keep the newest [PredictionTuning.SAMPLE_SIZE_TARGET]. Shared
 * by the interval-count and next-feed prediction use cases. Callers handle incomplete sessions first.
 */
internal fun validFeedIntervals(
    sessions: List<BreastfeedingSession>,
    zoneId: ZoneId,
    quietStartMinute: Int,
    quietEndMinute: Int,
): List<IntervalSample> =
    sessions.sortedByDescending { it.startTime }
        .zipWithNext { newer, older ->
            IntervalSample(
                endpointA = older.startTime,
                endpointB = newer.startTime,
                minutes = Duration.between(older.startTime, newer.startTime).toMinutes().toInt(),
            )
        }
        .filter { it.minutes <= PredictionTuning.INTERVAL_MAX_MINUTES }
        .filter {
            !isEndpointInQuietHours(it.endpointA, zoneId, quietStartMinute, quietEndMinute) &&
                !isEndpointInQuietHours(it.endpointB, zoneId, quietStartMinute, quietEndMinute)
        }
        .take(PredictionTuning.SAMPLE_SIZE_TARGET)
