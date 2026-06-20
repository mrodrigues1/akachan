package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.DoctorVisit
import java.time.Instant

@Entity(
    tableName = "doctor_visits",
    indices = [Index(value = ["date"])],
)
data class DoctorVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "date") val date: Long,
    @ColumnInfo(name = "provider_name") val providerName: String? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "snapshot_label") val snapshotLabel: String? = null,
    @ColumnInfo(name = "snapshot_created_at") val snapshotCreatedAt: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun DoctorVisitEntity.toDomain(): DoctorVisit = DoctorVisit(
    id = id,
    date = Instant.ofEpochMilli(date),
    providerName = providerName,
    notes = notes,
    snapshotLabel = snapshotLabel,
    snapshotCreatedAt = snapshotCreatedAt?.let { Instant.ofEpochMilli(it) },
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun DoctorVisit.toEntity(): DoctorVisitEntity = DoctorVisitEntity(
    id = id,
    date = date.toEpochMilli(),
    providerName = providerName,
    notes = notes,
    snapshotLabel = snapshotLabel,
    snapshotCreatedAt = snapshotCreatedAt?.toEpochMilli(),
    createdAt = createdAt.toEpochMilli(),
)
