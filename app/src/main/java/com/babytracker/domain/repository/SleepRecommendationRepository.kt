package com.babytracker.domain.repository

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome

data class NewSleepRecommendation(
    val anchorSleepId: Long,
    val generatedAt: Long,
    val type: String,
    val windowStart: Long,
    val windowEnd: Long,
    val bestEstimate: Long,
    val confidence: String,
    val lifecycle: RecommendationLifecycle,
    val algorithmVersion: String,
)

interface SleepRecommendationRepository {

    suspend fun insertRecommendation(recommendation: NewSleepRecommendation): Long

    suspend fun getIdByAnchorTypeVersion(
        anchorSleepId: Long,
        type: String,
        algorithmVersion: String,
    ): Long?

    /** Returns 1 if updated, 0 if row was already in a terminal state (FIRED or SUPERSEDED). */
    suspend fun updateLifecycle(id: Long, lifecycle: RecommendationLifecycle): Int

    suspend fun insertFeedback(
        recommendationId: Long,
        actualSleepRecordId: Long?,
        errorMinutes: Int?,
        outcome: RecommendationOutcome,
        createdAt: Long,
    ): Long

    suspend fun getLatestScheduledRecommendation(): SleepRecommendationSnapshot?
}

data class SleepRecommendationSnapshot(
    val id: Long,
    val anchorSleepId: Long,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val bestEstimateMs: Long,
)
