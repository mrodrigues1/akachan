package com.babytracker.data.repository

import com.babytracker.data.local.dao.GrowthMeasurementDao
import com.babytracker.data.local.entity.toDomain
import com.babytracker.data.local.entity.toEntity
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.repository.GrowthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrowthRepositoryImpl @Inject constructor(
    private val dao: GrowthMeasurementDao,
) : GrowthRepository {

    override suspend fun addMeasurement(measurement: GrowthMeasurement): Long =
        dao.insert(measurement.toEntity())

    override fun getAllMeasurements(): Flow<List<GrowthMeasurement>> =
        dao.getAll().mapList { it.toDomain() }

    override fun getMeasurementsByType(type: GrowthType): Flow<List<GrowthMeasurement>> =
        dao.getByType(type.name).mapList { it.toDomain() }

    override suspend fun deleteMeasurement(id: Long) = dao.deleteById(id)
}
