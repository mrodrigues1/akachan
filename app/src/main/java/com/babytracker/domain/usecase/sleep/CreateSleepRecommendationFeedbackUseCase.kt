package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationOutcome
import com.babytracker.domain.repository.SleepRecommendationRepository
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

class CreateSleepRecommendationFeedbackUseCase @Inject constructor(
    private val repository: SleepRecommendationRepository,
    private val nowProvider: () -> Instant,
) {
    suspend operator fun invoke(
        recommendationId: Long,
        outcome: RecommendationOutcome,
        actualSleepRecordId: Long? = null,
        sleepStartTime: Instant? = null,
        windowBestEstimate: Instant? = null,
    ): Long {
        val errorMinutes = if (sleepStartTime != null && windowBestEstimate != null) {
            Duration.between(windowBestEstimate, sleepStartTime).toMinutes().toInt()
        } else {
            null
        }
        return repository.insertFeedback(
            recommendationId = recommendationId,
            actualSleepRecordId = actualSleepRecordId,
            errorMinutes = errorMinutes,
            outcome = outcome,
            createdAt = nowProvider().toEpochMilli(),
        )
    }
}
