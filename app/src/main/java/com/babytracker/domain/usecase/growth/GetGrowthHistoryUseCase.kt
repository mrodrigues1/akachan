package com.babytracker.domain.usecase.growth

import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.repository.GrowthRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetGrowthHistoryUseCase @Inject constructor(
    private val repository: GrowthRepository,
) {
    operator fun invoke(): Flow<List<GrowthMeasurement>> = repository.getAllMeasurements()
}
