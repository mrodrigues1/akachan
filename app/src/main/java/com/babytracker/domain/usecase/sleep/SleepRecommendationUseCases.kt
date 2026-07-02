package com.babytracker.domain.usecase.sleep

import com.babytracker.domain.repository.SleepRecommendationRepository
import javax.inject.Inject

class SleepRecommendationUseCases @Inject constructor(
    val persist: PersistSleepRecommendationUseCase,
    val repository: SleepRecommendationRepository,
    val createFeedback: CreateSleepRecommendationFeedbackUseCase,
)
