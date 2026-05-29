package com.babytracker.widget.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import java.time.Instant

enum class SleepState {
    SLEEPING,
    AWAKE,
    NONE,
}

enum class FeedState {
    ACTIVE,
    PAUSED,
    RECENT,
    NONE,
}

data class WidgetData(
    val babyName: String,
    val lastFeedSide: BreastSide?,
    val lastFeedStart: Instant?,
    val feedState: FeedState,
    val sleepState: SleepState,
    val sleepSince: Instant?,
) {
    companion object {
        val EMPTY: WidgetData = WidgetData(
            babyName = "Baby",
            lastFeedSide = null,
            lastFeedStart = null,
            feedState = FeedState.NONE,
            sleepState = SleepState.NONE,
            sleepSince = null,
        )
    }
}

fun toWidgetData(
    babyName: String?,
    lastFeed: BreastfeedingSession?,
    latestSleep: SleepRecord?,
): WidgetData {
    val resolvedName = babyName?.takeIf { it.isNotBlank() } ?: "Baby"
    val sleepState = latestSleep.toSleepState()
    val sleepSince = latestSleep.toSleepSince(sleepState)

    return WidgetData(
        babyName = resolvedName,
        lastFeedSide = lastFeed.toEffectiveFeedSide(),
        lastFeedStart = lastFeed?.startTime,
        feedState = lastFeed.toFeedState(),
        sleepState = sleepState,
        sleepSince = sleepSince,
    )
}

private fun SleepRecord?.toSleepState(): SleepState =
    when {
        this == null -> SleepState.NONE
        endTime == null -> SleepState.SLEEPING
        else -> SleepState.AWAKE
    }

private fun SleepRecord?.toSleepSince(state: SleepState): Instant? =
    when (state) {
        SleepState.SLEEPING -> this?.startTime
        SleepState.AWAKE -> this?.endTime
        SleepState.NONE -> null
    }

private fun BreastfeedingSession?.toEffectiveFeedSide(): BreastSide? =
    this?.let { feed ->
        if (feed.switchTime != null) {
            feed.startingSide.opposite()
        } else {
            feed.startingSide
        }
    }

private fun BreastSide.opposite(): BreastSide =
    if (this == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT

private fun BreastfeedingSession?.toFeedState(): FeedState =
    when {
        this == null -> FeedState.NONE
        endTime != null -> FeedState.RECENT
        pausedAt != null -> FeedState.PAUSED
        else -> FeedState.ACTIVE
    }
