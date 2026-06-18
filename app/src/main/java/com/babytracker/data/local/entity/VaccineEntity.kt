package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.VaccineRecord
import com.babytracker.domain.model.toVaccineStatusSafe
import java.time.Instant

@Entity(
    tableName = "vaccines",
    indices = [Index(value = ["scheduled_date"]), Index(value = ["status"])],
)
data class VaccineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "dose_label") val doseLabel: String? = null,
    @ColumnInfo(name = "status") val status: String,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: Long? = null,
    @ColumnInfo(name = "administered_date") val administeredDate: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun VaccineEntity.toDomain(): VaccineRecord = VaccineRecord(
    id = id,
    name = name,
    doseLabel = doseLabel,
    status = status.toVaccineStatusSafe(),
    scheduledDate = scheduledDate?.let { Instant.ofEpochMilli(it) },
    administeredDate = administeredDate?.let { Instant.ofEpochMilli(it) },
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun VaccineRecord.toEntity(): VaccineEntity = VaccineEntity(
    id = id,
    name = name,
    doseLabel = doseLabel,
    status = status.name,
    scheduledDate = scheduledDate?.toEpochMilli(),
    administeredDate = administeredDate?.toEpochMilli(),
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
)
