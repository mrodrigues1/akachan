package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.PumpingBreast
import com.babytracker.domain.model.PumpingSession
import java.time.Instant

@Entity(
    tableName = "pumping_sessions",
    indices = [Index(value = ["start_time"], orders = [Index.Order.DESC])],
)
data class PumpingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "start_time") val startTime: Long,
    @ColumnInfo(name = "end_time") val endTime: Long? = null,
    @ColumnInfo(name = "breast") val breast: String,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "paused_at") val pausedAt: Long? = null,
    @ColumnInfo(name = "paused_duration_ms") val pausedDurationMs: Long = 0,
)

fun PumpingEntity.toDomain(): PumpingSession = PumpingSession(
    id = id,
    startTime = Instant.ofEpochMilli(startTime),
    endTime = endTime?.let { Instant.ofEpochMilli(it) },
    breast = PumpingBreast.entries.find { it.name == breast } ?: PumpingBreast.BOTH,
    volumeMl = volumeMl,
    notes = notes,
    pausedAt = pausedAt?.let { Instant.ofEpochMilli(it) },
    pausedDurationMs = pausedDurationMs,
)

fun PumpingSession.toEntity(): PumpingEntity = PumpingEntity(
    id = id,
    startTime = startTime.toEpochMilli(),
    endTime = endTime?.toEpochMilli(),
    breast = breast.name,
    volumeMl = volumeMl,
    notes = notes,
    pausedAt = pausedAt?.toEpochMilli(),
    pausedDurationMs = pausedDurationMs,
)
