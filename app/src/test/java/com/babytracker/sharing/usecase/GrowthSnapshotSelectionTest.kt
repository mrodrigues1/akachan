package com.babytracker.sharing.usecase

import com.babytracker.domain.model.GrowthMeasurement
import com.babytracker.domain.model.GrowthType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class GrowthSnapshotSelectionTest {

    @Test
    fun `latestPerType keeps every type even when one dominates the recent window`() {
        val manyWeights = (1..25).map {
            GrowthMeasurement(
                id = it.toLong(),
                takenAt = Instant.ofEpochMilli(10_000L + it),
                type = GrowthType.WEIGHT,
                valueCanonical = 5000,
            )
        }
        val oneOldLength = GrowthMeasurement(
            id = 100,
            takenAt = Instant.ofEpochMilli(500),
            type = GrowthType.LENGTH,
            valueCanonical = 600,
        )

        val selected = (manyWeights + oneOldLength).latestPerType()

        // The lone length measurement survives despite 25 newer weight entries.
        assertTrue(selected.any { it.type == GrowthType.LENGTH })
        // Weight is capped at the per-type limit.
        assertEquals(10, selected.count { it.type == GrowthType.WEIGHT })
    }
}
