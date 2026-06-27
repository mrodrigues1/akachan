package com.babytracker.domain.usecase.breastfeeding

import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.PredictionTuning
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/** A feed-to-feed gap between two consecutive sessions, with both endpoints kept for quiet-hours checks. */
internal data class RecentFeedInterval(
    val endpointA: Instant,
    val endpointB: Instant,
    val minutes: Int,
)

/**
 * Builds feed-to-feed intervals from [sessions], drops gaps over
 * [PredictionTuning.INTERVAL_MAX_MINUTES] and any whose endpoints fall in quiet hours, then keeps the
 * newest [PredictionTuning.SAMPLE_SIZE_TARGET]. Shared by [CountRecentValidIntervalsUseCase] and
 * [PredictNextFeedUseCase].
 */
internal fun recentValidIntervals(
    sessions: List<BreastfeedingSession>,
    zoneId: ZoneId,
    quietStartMinute: Int,
    quietEndMinute: Int,
): List<RecentFeedInterval> =
    sessions.sortedByDescending { it.startTime }
        .zipWithNext { newer, older ->
            RecentFeedInterval(
                endpointA = older.startTime,
                endpointB = newer.startTime,
                minutes = Duration.between(older.startTime, newer.startTime).toMinutes().toInt(),
            )
        }
        .filter { it.minutes <= PredictionTuning.INTERVAL_MAX_MINUTES }
        .filter { !isEndpointInQuietHours(it.endpointA, zoneId, quietStartMinute, quietEndMinute) }
        .filter { !isEndpointInQuietHours(it.endpointB, zoneId, quietStartMinute, quietEndMinute) }
        .take(PredictionTuning.SAMPLE_SIZE_TARGET)
