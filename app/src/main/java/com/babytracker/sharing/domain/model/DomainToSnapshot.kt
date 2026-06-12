package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.MilkBag
import com.babytracker.domain.model.SleepPredictionState
import com.babytracker.domain.model.SleepRecord
import java.time.ZoneOffset

fun Baby.toSnapshot(): BabySnapshot = BabySnapshot(
    name = name,
    birthDateMs = birthDate.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
    allergies = allergies.map { it.name },
)

fun BreastfeedingSession.toSnapshot(): SessionSnapshot = SessionSnapshot(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    startingSide = startingSide.name,
    switchTime = switchTime?.toEpochMilli(),
    pausedDurationMs = pausedDurationMs,
    notes = notes,
)

fun BottleFeed.toSnapshot(): BottleFeedSnapshot = BottleFeedSnapshot(
    timestamp = timestamp.toEpochMilli(),
    volumeMl = volumeMl,
    type = type.name,
    clientId = clientId,
    author = author.name,
    notes = notes,
)

fun MilkBag.toSnapshot(): MilkBagSnapshot = MilkBagSnapshot(
    id = id,
    collectionDateMs = collectionDate.toEpochMilli(),
    volumeMl = volumeMl,
    notes = notes,
)

fun SleepRecord.toSnapshot(): SleepSnapshot = SleepSnapshot(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    sleepType = sleepType.name,
    notes = notes,
)

fun SleepPredictionState.toSnapshot(
    generatedAt: Long = System.currentTimeMillis(),
): SleepPredictionSnapshot? =
    when (this) {
        is SleepPredictionState.Window -> SleepPredictionSnapshot(
            stateLabel = "WINDOW",
            windowStart = window.windowStart.toEpochMilli(),
            windowEnd = window.windowEnd.toEpochMilli(),
            bestEstimate = window.bestEstimate.toEpochMilli(),
            confidence = window.confidence.name,
            reasons = window.reasons,
            feedPrompt = window.feedPrompt,
            generatedAt = generatedAt,
        )
        is SleepPredictionState.NeedMoreData ->
            SleepPredictionSnapshot(stateLabel = "NEED_MORE_DATA", generatedAt = generatedAt)
        SleepPredictionState.CueLed ->
            SleepPredictionSnapshot(stateLabel = "CUE_LED", generatedAt = generatedAt)
        SleepPredictionState.CurrentlySleeping ->
            SleepPredictionSnapshot(stateLabel = "CURRENTLY_SLEEPING", generatedAt = generatedAt)
        SleepPredictionState.AfterActiveFeed ->
            SleepPredictionSnapshot(stateLabel = "AFTER_ACTIVE_FEED", generatedAt = generatedAt)
        SleepPredictionState.Overdue ->
            SleepPredictionSnapshot(stateLabel = "OVERDUE", generatedAt = generatedAt)
        is SleepPredictionState.Unavailable -> null
    }
