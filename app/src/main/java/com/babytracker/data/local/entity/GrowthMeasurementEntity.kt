package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import java.time.Instant

@Entity(
    tableName = "growth_measurements",
    indices = [
        Index(value = ["type"]),
        Index(value = ["taken_at"]),
    ],
)
data class GrowthMeasurementEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    @ColumnInfo(name = "type") val type: String,
    // Canonical units: grams for WEIGHT, millimetres for LENGTH/HEAD_CIRC.
    @ColumnInfo(name = "value_canonical") val valueCanonical: Long,
    @ColumnInfo(name = "notes") val notes: String? = null,
)

fun GrowthMeasurementEntity.toDomain(): GrowthMeasurement = GrowthMeasurement(
    id = id,
    takenAt = Instant.ofEpochMilli(takenAt),
    type = GrowthType.entries.find { it.name == type } ?: GrowthType.WEIGHT,
    valueCanonical = valueCanonical,
    notes = notes,
)

fun GrowthMeasurement.toEntity(): GrowthMeasurementEntity = GrowthMeasurementEntity(
    id = id,
    takenAt = takenAt.toEpochMilli(),
    type = type.name,
    valueCanonical = valueCanonical,
    notes = notes,
)
