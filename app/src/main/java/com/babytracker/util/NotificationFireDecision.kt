package com.babytracker.util

import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * Single pure home for "when does a predictive notification fire".
 *
 * Coordinators ask [decideSchedule] whether an alarm should be set at all; receivers ask
 * [decideFire] whether a delivered alarm should still show (inexact delivery can land late or
 * inside a quiet window configured after the alarm was set). Both share the one quiet-hours
 * and staleness definition, so wrong-time bugs have exactly one home.
 */

/** Predictions older than this past their best estimate are dropped instead of shown. */
const val PREDICTION_MAX_STALE_MINUTES = 20L

/**
 * Quiet-hours membership as a half-open interval [start, end) in minutes-of-day, wrapping
 * midnight when start > end. Equal start/end means quiet hours are disabled.
 */
fun isInQuietHours(
    instant: Instant,
    quietStartMinute: Int,
    quietEndMinute: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): Boolean {
    if (quietStartMinute == quietEndMinute) return false
    val localTime = instant.atZone(zone).toLocalTime()
    val minuteOfDay = localTime.hour * 60 + localTime.minute
    return if (quietStartMinute < quietEndMinute) {
        minuteOfDay in quietStartMinute until quietEndMinute
    } else {
        minuteOfDay >= quietStartMinute || minuteOfDay < quietEndMinute
    }
}

fun isPredictionStale(now: Instant, bestEstimate: Instant): Boolean =
    now.isAfter(bestEstimate.plus(Duration.ofMinutes(PREDICTION_MAX_STALE_MINUTES)))

sealed interface ScheduleDecision {
    data class Schedule(val triggerAt: Instant) : ScheduleDecision
    data object PastTrigger : ScheduleDecision
    data object QuietHours : ScheduleDecision
}

/** Should an alarm be scheduled [leadMinutes] before [bestEstimate]? */
fun decideSchedule(
    now: Instant,
    bestEstimate: Instant,
    leadMinutes: Int,
    quietStartMinute: Int,
    quietEndMinute: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): ScheduleDecision {
    val triggerAt = bestEstimate.minus(Duration.ofMinutes(leadMinutes.toLong()))
    return when {
        triggerAt.isBefore(now) -> ScheduleDecision.PastTrigger
        isInQuietHours(triggerAt, quietStartMinute, quietEndMinute, zone) -> ScheduleDecision.QuietHours
        else -> ScheduleDecision.Schedule(triggerAt)
    }
}

sealed interface FireDecision {
    data object Fire : FireDecision
    data object Stale : FireDecision
    data object QuietHours : FireDecision
}

/** Should an alarm that just fired still show its notification? */
fun decideFire(
    now: Instant,
    bestEstimate: Instant,
    quietStartMinute: Int,
    quietEndMinute: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): FireDecision = when {
    isPredictionStale(now, bestEstimate) -> FireDecision.Stale
    isInQuietHours(now, quietStartMinute, quietEndMinute, zone) -> FireDecision.QuietHours
    else -> FireDecision.Fire
}
