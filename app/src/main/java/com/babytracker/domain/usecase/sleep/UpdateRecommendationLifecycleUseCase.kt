package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.model.RecommendationLifecycle
import com.babytracker.domain.repository.SleepRecommendationRepository
import javax.inject.Inject

class UpdateRecommendationLifecycleUseCase @Inject constructor(
    private val repository: SleepRecommendationRepository,
) {
    suspend operator fun invoke(id: Long, lifecycle: RecommendationLifecycle): Int =
        repository.updateLifecycle(id, lifecycle)
}
