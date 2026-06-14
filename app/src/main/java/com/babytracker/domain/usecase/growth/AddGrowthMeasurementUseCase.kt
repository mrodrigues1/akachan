package com.babytracker.domain.usecase.growth

import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.repository.GrowthRepository
import javax.inject.Inject

class AddGrowthMeasurementUseCase @Inject constructor(
    private val repository: GrowthRepository,
) {
    suspend operator fun invoke(measurement: GrowthMeasurement): Long {
        require(measurement.valueCanonical > 0) { "Measurement value must be positive" }
        return repository.addMeasurement(measurement)
    }
}
