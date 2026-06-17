package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.toDiaperTypeSafe
import java.time.Instant

@Entity(
    tableName = "diaper_changes",
    indices = [Index(value = ["timestamp"])],
)
data class DiaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun DiaperEntity.toDomain(): DiaperChange = DiaperChange(
    id = id,
    timestamp = Instant.ofEpochMilli(timestamp),
    type = type.toDiaperTypeSafe(),
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun DiaperChange.toEntity(): DiaperEntity = DiaperEntity(
    id = id,
    timestamp = timestamp.toEpochMilli(),
    type = type.name,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
)
