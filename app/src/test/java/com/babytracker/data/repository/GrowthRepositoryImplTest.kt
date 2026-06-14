package com.babytracker.data.repository

import app.cash.turbine.test
import com.babytracker.data.local.dao.GrowthMeasurementDao
import com.babytracker.data.local.entity.GrowthMeasurementEntity
import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant

class GrowthRepositoryImplTest {

    private lateinit var dao: GrowthMeasurementDao
    private lateinit var repository: GrowthRepositoryImpl

    @BeforeEach
    fun setup() {
        dao = mockk()
        repository = GrowthRepositoryImpl(dao)
    }

    @Test
    fun `addMeasurement inserts canonical entity and returns new id`() = runTest {
        coEvery { dao.insert(any()) } returns 42L

        val id = repository.addMeasurement(
            GrowthMeasurement(
                takenAt = Instant.ofEpochMilli(1000),
                type = GrowthType.WEIGHT,
                valueCanonical = 5200,
                notes = "newborn",
            ),
        )

        assertEquals(42L, id)
        val slot = slot<GrowthMeasurementEntity>()
        coVerify { dao.insert(capture(slot)) }
        assertEquals("WEIGHT", slot.captured.type)
        assertEquals(5200L, slot.captured.valueCanonical)
        assertEquals(1000L, slot.captured.takenAt)
    }

    @Test
    fun `getAllMeasurements maps entities to domain`() = runTest {
        every { dao.getAll() } returns flowOf(
            listOf(
                GrowthMeasurementEntity(id = 1, takenAt = 2000, type = "LENGTH", valueCanonical = 600),
            ),
        )

        repository.getAllMeasurements().test {
            val list = awaitItem()
            assertEquals(1, list.size)
            assertEquals(GrowthType.LENGTH, list[0].type)
            assertEquals(600L, list[0].valueCanonical)
            awaitComplete()
        }
    }

    @Test
    fun `getMeasurementsByType passes enum name to dao`() = runTest {
        every { dao.getByType("HEAD_CIRC") } returns flowOf(emptyList())

        repository.getMeasurementsByType(GrowthType.HEAD_CIRC).test {
            assertEquals(0, awaitItem().size)
            awaitComplete()
        }
        verify { dao.getByType("HEAD_CIRC") }
    }

    @Test
    fun `deleteMeasurement delegates to dao`() = runTest {
        coEvery { dao.deleteById(7) } just Runs

        repository.deleteMeasurement(7)

        coVerify { dao.deleteById(7) }
    }
}
