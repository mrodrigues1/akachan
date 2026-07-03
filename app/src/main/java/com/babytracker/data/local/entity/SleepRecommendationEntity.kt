package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_recommendations",
    indices = [
        Index("generated_at"),
        Index("anchor_sleep_id"),
        Index("recommendation_type"),
        Index(value = ["anchor_sleep_id", "recommendation_type", "algorithm_version"], unique = true),
        Index(value = ["lifecycle", "generated_at"]),
    ],
)
data class SleepRecommendationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "anchor_sleep_id") val anchorSleepId: Long,
    @ColumnInfo(name = "generated_at") val generatedAt: Long,
    @ColumnInfo(name = "recommendation_type") val recommendationType: String,
    @ColumnInfo(name = "window_start") val windowStart: Long,
    @ColumnInfo(name = "window_end") val windowEnd: Long,
    @ColumnInfo(name = "best_estimate") val bestEstimate: Long,
    @ColumnInfo(name = "confidence") val confidence: String,
    @ColumnInfo(name = "lifecycle") val lifecycle: String,
    @ColumnInfo(name = "algorithm_version") val algorithmVersion: String,
)
