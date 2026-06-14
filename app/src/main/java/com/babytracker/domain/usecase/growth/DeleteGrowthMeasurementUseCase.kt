package com.babytracker.domain.usecase.growth

import com.babytracker.domain.repository.GrowthRepository
import javax.inject.Inject

class DeleteGrowthMeasurementUseCase @Inject constructor(
    private val repository: GrowthRepository,
) {
    suspend operator fun invoke(id: Long) = repository.deleteMeasurement(id)
}
