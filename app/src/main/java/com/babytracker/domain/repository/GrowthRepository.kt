package com.babytracker.domain.repository

import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import kotlinx.coroutines.flow.Flow

interface GrowthRepository {
    suspend fun addMeasurement(measurement: GrowthMeasurement): Long
    fun getAllMeasurements(): Flow<List<GrowthMeasurement>>
    fun getMeasurementsByType(type: GrowthType): Flow<List<GrowthMeasurement>>
    suspend fun deleteMeasurement(id: Long)
}
