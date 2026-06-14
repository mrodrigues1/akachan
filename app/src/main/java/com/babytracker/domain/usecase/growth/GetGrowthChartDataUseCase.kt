package com.babytracker.domain.usecase.growth

import com.babytracker.domain.growth.GrowthChartData
import com.babytracker.domain.growth.GrowthPlotPoint
import com.babytracker.domain.growth.WhoPercentileCalculator
import com.babytracker.domain.growth.WhoReferenceData
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.growth.latestByRecency
import com.babytracker.domain.repository.GrowthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject

private const val DAYS_PER_MONTH = 30.4375

/**
 * Joins the stored measurements for one [GrowthType] with the WHO reference
 * curves and the baby's age/sex to produce a fully-resolved [GrowthChartData].
 */
class GetGrowthChartDataUseCase @Inject constructor(
    private val growthRepository: GrowthRepository,
    private val babyRepository: BabyRepository,
    private val whoReferenceData: WhoReferenceData,
) {
    operator fun invoke(type: GrowthType): Flow<GrowthChartData> =
        combine(
            babyRepository.getBabyProfile(),
            growthRepository.getMeasurementsByType(type),
        ) { baby, measurements ->
            buildChartData(type, baby, measurements)
        }

    private suspend fun buildChartData(
        type: GrowthType,
        baby: Baby?,
        measurements: List<GrowthMeasurement>,
    ): GrowthChartData {
        val sex = baby?.sex ?: BabySex.UNSPECIFIED
        val birthDate = baby?.birthDate
        val lms = whoReferenceData.lmsTable(type, sex)
        val curves = if (lms.isEmpty()) emptyList() else WhoPercentileCalculator.curves(lms)

        val plotted = if (birthDate == null) {
            emptyList()
        } else {
            measurements.map { measurement ->
                GrowthPlotPoint(
                    measurementId = measurement.id,
                    ageMonths = ageInMonths(birthDate, measurement.takenAt),
                    value = WhoPercentileCalculator.canonicalToWhoUnit(type, measurement.valueCanonical),
                )
            }
        }

        val latest = measurements.latestByRecency()
        val latestPercentile = if (lms.isEmpty() || birthDate == null || latest == null) {
            null
        } else {
            WhoPercentileCalculator.percentileForCanonical(
                type = type,
                valueCanonical = latest.valueCanonical,
                ageMonths = ageInMonths(birthDate, latest.takenAt),
                points = lms,
            )
        }

        return GrowthChartData(
            type = type,
            measurements = measurements,
            plotted = plotted,
            curves = curves,
            latestPercentile = latestPercentile,
            isSexSpecified = sex != BabySex.UNSPECIFIED,
        )
    }

    private fun ageInMonths(birthDate: LocalDate, takenAt: Instant): Double {
        val measurementDate = takenAt.atZone(ZoneOffset.UTC).toLocalDate()
        return ChronoUnit.DAYS.between(birthDate, measurementDate) / DAYS_PER_MONTH
    }
}
