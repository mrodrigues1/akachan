package com.babytracker.domain.usecase.growth

import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.repository.GrowthRepository
import javax.inject.Inject

/**
 * Edits an existing measurement. The Room insert is REPLACE-on-conflict, so re-inserting a
 * measurement that carries its existing [GrowthMeasurement.id] overwrites that row in place.
 */
class UpdateGrowthMeasurementUseCase @Inject constructor(
    private val repository: GrowthRepository,
) {
    suspend operator fun invoke(measurement: GrowthMeasurement): Long {
        require(measurement.id != 0L) { "Updating requires an existing measurement id" }
        require(measurement.valueCanonical > 0) { "Measurement value must be positive" }
        return repository.addMeasurement(measurement)
    }
}
