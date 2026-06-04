package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.babytracker.domain.model.BreastSide
import com.babytracker.domain.model.BreastfeedingSession
import java.time.Instant

@Entity(tableName = "breastfeeding_sessions")
data class BreastfeedingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "starting_side") val startingSide: String,
    @ColumnInfo(name = "switch_time") val switchTime: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "paused_at") val pausedAt: Long? = null,
    @ColumnInfo(name = "paused_duration_ms") val pausedDurationMs: Long = 0
)

fun BreastfeedingEntity.toDomain(): BreastfeedingSession = BreastfeedingSession(
    id = id,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    startingSide = BreastSide.valueOf(startingSide),
    switchTime = switchTime?.let { Instant.ofEpochMilli(it) },
    notes = notes,
    pausedAt = pausedAt?.let { Instant.ofEpochMilli(it) },
    pausedDurationMs = pausedDurationMs
)

fun BreastfeedingSession.toEntity(): BreastfeedingEntity = BreastfeedingEntity(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    startingSide = startingSide.name,
    switchTime = switchTime?.toEpochMilli(),
    notes = notes,
    pausedAt = pausedAt?.toEpochMilli(),
    pausedDurationMs = pausedDurationMs
)
