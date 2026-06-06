package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.model.SleepPredictionTuning
import com.babytracker.domain.model.SleepWindow
import com.babytracker.domain.repository.NewSleepRecommendation
import com.babytracker.domain.repository.SleepRecommendationRepository
import java.time.Instant
import javax.inject.Inject

class PersistSleepRecommendationUseCase @Inject constructor(
    private val repository: SleepRecommendationRepository,
    private val nowProvider: () -> Instant,
) {
    suspend operator fun invoke(
        anchorSleepId: Long,
        window: SleepWindow,
        recommendationType: String,
    ): Long {
        val insertedId = repository.insertRecommendation(
            NewSleepRecommendation(
                anchorSleepId = anchorSleepId,
                generatedAt = nowProvider().toEpochMilli(),
                type = recommendationType,
                windowStart = window.windowStart.toEpochMilli(),
                windowEnd = window.windowEnd.toEpochMilli(),
                bestEstimate = window.bestEstimate.toEpochMilli(),
                confidence = window.confidence.name,
                lifecycle = RecommendationLifecycle.GENERATED,
                algorithmVersion = SleepPredictionTuning.ALGORITHM_VERSION,
            )
        )
        return if (insertedId == -1L) {
            repository.getIdByAnchorTypeVersion(
                anchorSleepId = anchorSleepId,
                type = recommendationType,
                algorithmVersion = SleepPredictionTuning.ALGORITHM_VERSION,
            ) ?: error(
                "Recommendation dedup: insert ignored but no existing row for anchor=$anchorSleepId type=$recommendationType"
            )
        } else {
            insertedId
        }
    }
}
