package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.BabyEvent
import com.babytracker.domain.model.BabyEventType
import java.time.Instant

@Entity(
    tableName = "baby_events",
    indices = [
        Index(value = ["timestamp"]),
        Index(value = ["event_type"]),
    ],
)
data class BabyEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "event_type") val eventType: String,
    @ColumnInfo(name = "intensity") val intensity: Int? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun BabyEventEntity.toDomain(): BabyEvent = BabyEvent(
    id = id,
    timestamp = Instant.ofEpochMilli(timestamp),
    type = BabyEventType.entries.find { it.name == eventType } ?: BabyEventType.FUSSY,
    intensity = intensity,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun BabyEvent.toEntity(): BabyEventEntity = BabyEventEntity(
    id = id,
    timestamp = timestamp.toEpochMilli(),
    eventType = type.name,
    intensity = intensity,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
)
