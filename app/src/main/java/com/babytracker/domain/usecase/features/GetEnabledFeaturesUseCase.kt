package com.babytracker.domain.usecase.features

import com.babytracker.domain.model.AppFeature
import com.babytracker.domain.repository.FeatureToggleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetEnabledFeaturesUseCase @Inject constructor(
    private val repository: FeatureToggleRepository,
) {
    operator fun invoke(): Flow<Set<AppFeature>> = repository.getEnabledFeatures()
}
