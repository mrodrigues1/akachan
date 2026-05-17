package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.MilkBag
import java.time.Instant

@Entity(
    tableName = "milk_bags",
    foreignKeys = [
        ForeignKey(
            entity = PumpingEntity::class,
            parentColumns = ["id"],
            childColumns = ["source_session_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("used_at"),
        Index("collection_date"),
        Index("source_session_id"),
    ],
)
data class MilkBagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "collection_date") val collectionDate: Long,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int,
    @ColumnInfo(name = "source_session_id") val sourceSessionId: Long? = null,
    @ColumnInfo(name = "used_at") val usedAt: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)

fun MilkBagEntity.toDomain(): MilkBag = MilkBag(
    id = id,
    collectionDate = Instant.ofEpochMilli(collectionDate),
    volumeMl = volumeMl,
    sourceSessionId = sourceSessionId,
    usedAt = usedAt?.let { Instant.ofEpochMilli(it) },
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
)

fun MilkBag.toEntity(): MilkBagEntity = MilkBagEntity(
    id = id,
    collectionDate = collectionDate.toEpochMilli(),
    volumeMl = volumeMl,
    sourceSessionId = sourceSessionId,
    usedAt = usedAt?.toEpochMilli(),
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
)
