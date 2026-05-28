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

data class WidgetData(
    val babyName: String,
    val lastFeedSide: BreastSide?,
    val lastFeedStart: Instant?,
    val sleepState: SleepState,
    val sleepSince: Instant?,
) {
    companion object {
        val EMPTY: WidgetData = WidgetData(
            babyName = "Baby",
            lastFeedSide = null,
            lastFeedStart = null,
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

    val sleepState = when {
        latestSleep == null -> SleepState.NONE
        latestSleep.endTime == null -> SleepState.SLEEPING
        else -> SleepState.AWAKE
    }
    val sleepSince: Instant? = when (sleepState) {
        SleepState.SLEEPING -> latestSleep?.startTime
        SleepState.AWAKE -> latestSleep?.endTime
        SleepState.NONE -> null
    }

    val effectiveFeedSide = lastFeed?.let { feed ->
        if (feed.switchTime != null) {
            if (feed.startingSide == BreastSide.LEFT) BreastSide.RIGHT else BreastSide.LEFT
        } else {
            feed.startingSide
        }
    }

    return WidgetData(
        babyName = resolvedName,
        lastFeedSide = effectiveFeedSide,
        lastFeedStart = lastFeed?.startTime,
        sleepState = sleepState,
        sleepSince = sleepSince,
    )
}
