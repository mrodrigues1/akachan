package com.babytracker.data.repository

import com.babytracker.data.local.dao.SleepRecommendationDao
import com.babytracker.data.local.entity.SleepRecommendationEntity
import com.babytracker.data.local.entity.SleepRecommendationFeedbackEntity
import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.NewSleepRecommendation
import com.babytracker.domain.repository.SleepRecommendationRepository
import com.babytracker.domain.repository.SleepRecommendationSnapshot
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepRecommendationRepositoryImpl @Inject constructor(
    private val dao: SleepRecommendationDao,
) : SleepRecommendationRepository {

    override suspend fun insertRecommendation(recommendation: NewSleepRecommendation): Long =
        dao.insertRecommendation(
            SleepRecommendationEntity(
                anchorSleepId = recommendation.anchorSleepId,
                generatedAt = recommendation.generatedAt,
                recommendationType = recommendation.type,
                windowStart = recommendation.windowStart,
                windowEnd = recommendation.windowEnd,
                bestEstimate = recommendation.bestEstimate,
                confidence = recommendation.confidence,
                lifecycle = recommendation.lifecycle.name,
                algorithmVersion = recommendation.algorithmVersion,
            )
        )

    override suspend fun getIdByAnchorTypeVersion(
        anchorSleepId: Long,
        type: String,
        algorithmVersion: String,
    ): Long? = dao.getByAnchorTypeVersion(anchorSleepId, type, algorithmVersion)?.id

    override suspend fun updateLifecycle(id: Long, lifecycle: RecommendationLifecycle): Int =
        dao.updateLifecycle(id, lifecycle.name)

    override suspend fun insertFeedback(
        recommendationId: Long,
        actualSleepRecordId: Long?,
        errorMinutes: Int?,
        outcome: RecommendationOutcome,
        createdAt: Long,
    ): Long = dao.insertFeedback(
        SleepRecommendationFeedbackEntity(
            recommendationId = recommendationId,
            actualSleepRecordId = actualSleepRecordId,
            errorMinutes = errorMinutes,
            outcome = outcome.name,
            createdAt = createdAt,
        )
    )

    override suspend fun getLatestScheduledRecommendation(): SleepRecommendationSnapshot? =
        dao.getLatestScheduled()?.let { entity ->
            SleepRecommendationSnapshot(
                id = entity.id,
                anchorSleepId = entity.anchorSleepId,
                windowStartMs = entity.windowStart,
                windowEndMs = entity.windowEnd,
                bestEstimateMs = entity.bestEstimate,
            )
        }
}
