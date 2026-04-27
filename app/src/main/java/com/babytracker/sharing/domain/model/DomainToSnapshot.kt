package com.babytracker.sharing.domain.model

import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BreastfeedingSession
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

fun SleepRecord.toSnapshot(): SleepSnapshot = SleepSnapshot(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    sleepType = sleepType.name,
    notes = notes,
)
