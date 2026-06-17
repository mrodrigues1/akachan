package com.babytracker.widget.data

import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.SleepRecord
import com.babytracker.domain.model.SleepType
import com.babytracker.sharing.domain.model.SessionSnapshot
import com.babytracker.sharing.domain.model.ShareSnapshot
import com.babytracker.sharing.domain.model.SleepSnapshot
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
    val feedEnabled: Boolean = true,
    val sleepEnabled: Boolean = true,
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
    feedEnabled: Boolean = true,
    sleepEnabled: Boolean = true,
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
        feedEnabled = feedEnabled,
        sleepEnabled = sleepEnabled,
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

fun toWidgetData(snapshot: ShareSnapshot): WidgetData {
    val lastFeed = snapshot.sessions
        .maxByOrNull { it.startTime }
        ?.toDomainSession()
    val latestSleep = snapshot.sleepRecords
        .maxByOrNull { it.startTime }
        ?.toDomainSleep()

    return toWidgetData(
        babyName = snapshot.baby.name,
        lastFeed = lastFeed,
        latestSleep = latestSleep,
    )
}

// Snapshot rows carry no pausedAt, so partner feeds never derive PAUSED (accepted limitation:
// the shared snapshot model has no pause state — the dashboard has the same blind spot).
private fun SessionSnapshot.toDomainSession(): BreastfeedingSession =
    BreastfeedingSession(
        id = id,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = endTime?.let(Instant::ofEpochMilli),
        startingSide = BreastSide.valueOf(startingSide),
        switchTime = switchTime?.let(Instant::ofEpochMilli),
        pausedAt = null,
    )

private fun SleepSnapshot.toDomainSleep(): SleepRecord =
    SleepRecord(
        id = id,
        startTime = Instant.ofEpochMilli(startTime),
        endTime = endTime?.let(Instant::ofEpochMilli),
        sleepType = SleepType.valueOf(sleepType),
    )
