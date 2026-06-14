package com.babytracker.domain.usecase.growth

import app.cash.turbine.test
import com.babytracker.domain.growth.LmsPoint
import com.babytracker.domain.growth.WhoReferenceData
import com.babytracker.domain.model.Baby
import com.babytracker.domain.model.BabySex
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import com.babytracker.domain.repository.BabyRepository
import com.babytracker.domain.repository.GrowthRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class GetGrowthChartDataUseCaseTest {

    private lateinit var growthRepository: GrowthRepository
    private lateinit var babyRepository: BabyRepository
    private lateinit var whoReferenceData: WhoReferenceData
    private lateinit var useCase: GetGrowthChartDataUseCase

    private val birthDate = LocalDate.of(2026, 1, 1)
    private val boysWeightLms = listOf(
        LmsPoint(0, 0.3487, 3.3464, 0.14602),
        LmsPoint(1, 0.2297, 4.4709, 0.13395),
    )

    @BeforeEach
    fun setup() {
        growthRepository = mockk()
        babyRepository = mockk()
        whoReferenceData = mockk()
        useCase = GetGrowthChartDataUseCase(growthRepository, babyRepository, whoReferenceData)
    }

    private fun birthInstant(): Instant = birthDate.atStartOfDay(ZoneOffset.UTC).toInstant()

    @Test
    fun `produces curves and median percentile for a known sex`() = runTest {
        val baby = Baby(name = "Leo", birthDate = birthDate, sex = BabySex.MALE)
        val measurement = GrowthMeasurement(
            id = 1,
            takenAt = birthInstant(),
            type = GrowthType.WEIGHT,
            valueCanonical = 3346, // grams ~ the 0-month median
        )
        every { babyRepository.getBabyProfile() } returns flowOf(baby)
        every { growthRepository.getMeasurementsByType(GrowthType.WEIGHT) } returns flowOf(listOf(measurement))
        coEvery { whoReferenceData.lmsTable(GrowthType.WEIGHT, BabySex.MALE) } returns boysWeightLms

        useCase(GrowthType.WEIGHT).test {
            val data = awaitItem()
            assertTrue(data.isSexSpecified)
            assertEquals(5, data.curves.size)
            assertEquals(1, data.plotted.size)
            assertEquals(50.0, data.latestPercentile!!, 1.0)
            awaitComplete()
        }
    }

    @Test
    fun `latest percentile uses the most recent same-day insert by id`() = runTest {
        val baby = Baby(name = "Leo", birthDate = birthDate, sex = BabySex.MALE)
        // Two measurements on the same day (identical takenAt); the higher id is the correction.
        val earlier = GrowthMeasurement(id = 1, takenAt = birthInstant(), type = GrowthType.WEIGHT, valueCanonical = 3346)
        val correction = GrowthMeasurement(id = 2, takenAt = birthInstant(), type = GrowthType.WEIGHT, valueCanonical = 4500)
        every { babyRepository.getBabyProfile() } returns flowOf(baby)
        every { growthRepository.getMeasurementsByType(GrowthType.WEIGHT) } returns
            flowOf(listOf(earlier, correction))
        coEvery { whoReferenceData.lmsTable(GrowthType.WEIGHT, BabySex.MALE) } returns boysWeightLms

        useCase(GrowthType.WEIGHT).test {
            val data = awaitItem()
            // 4.5 kg at the 0-month median (3.35 kg) is well above the 50th percentile.
            assertTrue(data.latestPercentile!! > 90.0)
            awaitComplete()
        }
    }

    @Test
    fun `suppresses curves and percentile when sex is unspecified`() = runTest {
        val baby = Baby(name = "Leo", birthDate = birthDate, sex = BabySex.UNSPECIFIED)
        val measurement = GrowthMeasurement(
            id = 1,
            takenAt = birthInstant(),
            type = GrowthType.WEIGHT,
            valueCanonical = 3346,
        )
        every { babyRepository.getBabyProfile() } returns flowOf(baby)
        every { growthRepository.getMeasurementsByType(GrowthType.WEIGHT) } returns flowOf(listOf(measurement))
        coEvery { whoReferenceData.lmsTable(GrowthType.WEIGHT, BabySex.UNSPECIFIED) } returns emptyList()

        useCase(GrowthType.WEIGHT).test {
            val data = awaitItem()
            assertFalse(data.isSexSpecified)
            assertTrue(data.curves.isEmpty())
            assertNull(data.latestPercentile)
            assertEquals(1, data.plotted.size) // raw points still plottable
            awaitComplete()
        }
    }
}
