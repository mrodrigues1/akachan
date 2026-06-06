package com.babytracker.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.babytracker.data.local.entity.SleepRecommendationEntity
import com.babytracker.data.local.entity.SleepRecommendationFeedbackEntity

@Dao
interface SleepRecommendationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecommendation(entity: SleepRecommendationEntity): Long

    @Query(
        """
        SELECT * FROM sleep_recommendations
        WHERE anchor_sleep_id = :anchorId
          AND recommendation_type = :type
          AND algorithm_version = :version
        LIMIT 1
        """,
    )
    suspend fun getByAnchorTypeVersion(
        anchorId: Long,
        type: String,
        version: String,
    ): SleepRecommendationEntity?

    // Returns 0 when the row is already in a terminal state (FIRED or SUPERSEDED), so callers
    // can detect stale events without re-reading the row.
    @Query(
        "UPDATE sleep_recommendations SET lifecycle = :lifecycle " +
            "WHERE id = :id AND lifecycle NOT IN ('FIRED', 'SUPERSEDED')",
    )
    suspend fun updateLifecycle(id: Long, lifecycle: String): Int

    @Query(
        """
        SELECT * FROM sleep_recommendations
        WHERE lifecycle = 'SCHEDULED'
        ORDER BY generated_at DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestScheduled(): SleepRecommendationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFeedback(entity: SleepRecommendationFeedbackEntity): Long
}
