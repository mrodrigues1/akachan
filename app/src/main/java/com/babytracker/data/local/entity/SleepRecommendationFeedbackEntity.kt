package com.babytracker.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sleep_recommendation_feedback",
    indices = [
        Index("recommendation_id"),
        Index("actual_sleep_record_id"),
        Index(value = ["recommendation_id", "outcome"], unique = true),
    ],
)
data class SleepRecommendationFeedbackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "recommendation_id") val recommendationId: Long,
    @ColumnInfo(name = "actual_sleep_record_id") val actualSleepRecordId: Long? = null,
    @ColumnInfo(name = "error_minutes") val errorMinutes: Int? = null,
    @ColumnInfo(name = "outcome") val outcome: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
