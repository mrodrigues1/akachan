package com.babytracker.data.local.entity

import com.babytracker.domain.model.BreastSide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BreastfeedingEntityMappingTest {

    @Test
    fun `toDomain parses known starting side`() {
        val entity = BreastfeedingEntity(startTime = 1_000, startingSide = "RIGHT")

        assertEquals(BreastSide.RIGHT, entity.toDomain().startingSide)
    }

    @Test
    fun `toDomain falls back on unknown starting side instead of crashing`() {
        val entity = BreastfeedingEntity(startTime = 1_000, startingSide = "GARBAGE")

        assertEquals(BreastSide.LEFT, entity.toDomain().startingSide)
    }
}
