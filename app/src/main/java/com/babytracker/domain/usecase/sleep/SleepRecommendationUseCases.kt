package com.babytracker.domain.usecase.sleep

import javax.inject.Inject

class SleepRecommendationUseCases @Inject constructor(
    val persist: PersistSleepRecommendationUseCase,
    val updateLifecycle: UpdateRecommendationLifecycleUseCase,
    val createFeedback: CreateSleepRecommendationFeedbackUseCase,
)
