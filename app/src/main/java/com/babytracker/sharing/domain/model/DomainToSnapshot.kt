package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DoctorVisit
import com.babytracker.domain.model.BreastfeedingSession
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.Milestone
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
    pausedAtMs = pausedAt?.toEpochMilli(),
)

fun BottleFeed.toSnapshot(): BottleFeedSnapshot = BottleFeedSnapshot(
    timestamp = timestamp.toEpochMilli(),
    volumeMl = volumeMl,
    type = type.name,
    clientId = clientId,
    author = author.name,
    notes = notes,
)

fun DiaperChange.toSnapshot(): DiaperSnapshot = DiaperSnapshot(
    timestamp = timestamp.toEpochMilli(),
    type = type.name,
    notes = notes,
)

// Visit-only: questions, notes, and the local snapshot reference are intentionally not synced.
fun DoctorVisit.toSnapshot(): DoctorVisitSnapshot = DoctorVisitSnapshot(
    date = date.toEpochMilli(),
    providerName = providerName,
)

fun MilkBag.toSnapshot(): MilkBagSnapshot = MilkBagSnapshot(
    id = id,
    collectionDateMs = collectionDate.toEpochMilli(),
    volumeMl = volumeMl,
    notes = notes,
)

fun GrowthMeasurement.toSnapshot(): GrowthSnapshot = GrowthSnapshot(
    type = type.name,
    takenAtMs = takenAt.toEpochMilli(),
    valueCanonical = valueCanonical,
    notes = notes,
)

// Photo is intentionally dropped: milestone photos are never synced to Firestore.
fun Milestone.toSnapshot(): MilestoneSnapshot = MilestoneSnapshot(
    title = title,
    dateEpochDay = date.toEpochDay(),
    timeMinuteOfDay = time?.let { it.hour * MINUTES_PER_HOUR + it.minute },
    note = note,
)

private const val MINUTES_PER_HOUR = 60

fun SleepRecord.toSnapshot(): SleepSnapshot = SleepSnapshot(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    sleepType = sleepType,
    notes = notes,
    clientId = clientId,
    startedBy = startedBy,
)

fun SleepPredictionState.toSnapshot(generatedAt: Long): SleepPredictionSnapshot? =
    when (this) {
        is SleepPredictionState.Window -> SleepPredictionSnapshot(
            stateLabel = "WINDOW",
            windowStart = window.windowStart.toEpochMilli(),
            windowEnd = window.windowEnd.toEpochMilli(),
            bestEstimate = window.bestEstimate.toEpochMilli(),
            confidence = window.confidence.name,
            reasons = window.reasons,
            feedDue = window.feedDue,
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
