package com.babytracker.export.data

import com.babytracker.data.local.entity.BottleFeedEntity
import com.babytracker.data.local.entity.BreastfeedingEntity
import com.babytracker.data.local.entity.GrowthMeasurementEntity
import com.babytracker.data.local.entity.MilestoneAchievementEntity
import com.babytracker.data.local.entity.MilkBagEntity
import com.babytracker.data.local.entity.PumpingEntity
import com.babytracker.data.local.entity.SleepEntity
import com.babytracker.export.domain.model.BottleFeedBackup
import com.babytracker.export.domain.model.BreastfeedingBackup
import com.babytracker.export.domain.model.GrowthBackup
import com.babytracker.export.domain.model.MilestoneBackup
import com.babytracker.export.domain.model.MilkBagBackup
import com.babytracker.export.domain.model.PumpingBackup
import com.babytracker.export.domain.model.SleepBackup
import java.util.UUID

fun BreastfeedingEntity.toBackup() = BreastfeedingBackup(
    id = id, startTime = startTime, endTime = endTime, startingSide = startingSide,
    switchTime = switchTime, notes = notes, pausedAt = pausedAt, pausedDurationMs = pausedDurationMs,
)

fun BreastfeedingBackup.toEntity() = BreastfeedingEntity(
    id = id, startTime = startTime, endTime = endTime, startingSide = startingSide,
    switchTime = switchTime, notes = notes, pausedAt = pausedAt, pausedDurationMs = pausedDurationMs,
)

fun GrowthMeasurementEntity.toBackup() = GrowthBackup(
    id = id, takenAtMs = takenAt, type = type, valueCanonical = valueCanonical, notes = notes,
)

fun GrowthBackup.toEntity() = GrowthMeasurementEntity(
    takenAt = takenAtMs, type = type, valueCanonical = valueCanonical, notes = notes,
)

// Photos are not archived in backups, so the restored entity has no photoUri.
fun MilestoneAchievementEntity.toBackup() = MilestoneBackup(
    milestone = milestone, achievedOnEpochDay = achievedOnEpochDay, notes = notes,
)

fun MilestoneBackup.toEntity() = MilestoneAchievementEntity(
    milestone = milestone, achievedOnEpochDay = achievedOnEpochDay, photoUri = null, notes = notes,
)

fun SleepEntity.toBackup() = SleepBackup(
    id = id, startTime = startTime, endTime = endTime, sleepType = sleepType, notes = notes,
    timezoneId = timezoneId,
)

fun SleepBackup.toEntity() = SleepEntity(
    id = id, startTime = startTime, endTime = endTime, sleepType = sleepType, notes = notes,
    timezoneId = timezoneId,
)

fun PumpingEntity.toBackup() = PumpingBackup(
    id = id, startTime = startTime, endTime = endTime, breast = breast,
    volumeMl = volumeMl, notes = notes, pausedAt = pausedAt, pausedDurationMs = pausedDurationMs,
)

fun PumpingBackup.toEntity() = PumpingEntity(
    id = id, startTime = startTime, endTime = endTime, breast = breast,
    volumeMl = volumeMl, notes = notes, pausedAt = pausedAt, pausedDurationMs = pausedDurationMs,
)

fun MilkBagEntity.toBackup() = MilkBagBackup(
    id = id, collectionDate = collectionDate, volumeMl = volumeMl, sourceSessionId = sourceSessionId,
    usedAt = usedAt, notes = notes, createdAt = createdAt,
)

fun MilkBagBackup.toEntity() = MilkBagEntity(
    id = id, collectionDate = collectionDate, volumeMl = volumeMl, sourceSessionId = sourceSessionId,
    usedAt = usedAt, notes = notes, createdAt = createdAt,
)

fun BottleFeedEntity.toBackup() = BottleFeedBackup(
    id = id, timestamp = timestamp, volumeMl = volumeMl, type = type,
    linkedMilkBagId = linkedMilkBagId, notes = notes, createdAt = createdAt,
)

// Backups predate clientId, so imports mint a fresh identity per entity.
fun BottleFeedBackup.toEntity() = BottleFeedEntity(
    id = id, clientId = UUID.randomUUID().toString(), timestamp = timestamp, volumeMl = volumeMl,
    type = type, linkedMilkBagId = linkedMilkBagId, notes = notes, createdAt = createdAt,
)
