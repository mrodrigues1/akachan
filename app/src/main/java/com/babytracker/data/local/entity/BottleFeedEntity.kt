package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.babytracker.domain.model.BottleFeed
import com.babytracker.domain.model.FeedAuthor
import com.babytracker.domain.model.FeedType
import java.time.Instant

@Entity(
    tableName = "bottle_feeds",
    foreignKeys = [
        ForeignKey(
            entity = MilkBagEntity::class,
            parentColumns = ["id"],
            childColumns = ["linked_milk_bag_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("timestamp"),
        Index("linked_milk_bag_id"),
        Index(value = ["client_id"], unique = true),
    ],
)
data class BottleFeedEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "client_id", defaultValue = "''") val clientId: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "volume_ml") val volumeMl: Int,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "linked_milk_bag_id") val linkedMilkBagId: Long? = null,
    @ColumnInfo(name = "notes") val notes: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "author", defaultValue = "'OWNER'") val author: String = FeedAuthor.OWNER.name,
)

fun BottleFeedEntity.toDomain(): BottleFeed = BottleFeed(
    id = id,
    clientId = clientId,
    timestamp = Instant.ofEpochMilli(timestamp),
    volumeMl = volumeMl,
    type = FeedType.valueOf(type),
    linkedMilkBagId = linkedMilkBagId,
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAt),
    author = FeedAuthor.valueOf(author),
)

fun BottleFeed.toEntity(): BottleFeedEntity = BottleFeedEntity(
    id = id,
    clientId = clientId,
    timestamp = timestamp.toEpochMilli(),
    volumeMl = volumeMl,
    type = type.name,
    linkedMilkBagId = linkedMilkBagId,
    notes = notes,
    createdAt = createdAt.toEpochMilli(),
    author = author.name,
)
