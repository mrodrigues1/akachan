package com.babytracker.data.local.entity

import com.babytracker.domain.model.PumpingBreast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PumpingEntityMappingTest {

    @Test
    fun `toDomain parses known breast`() {
        val entity = PumpingEntity(startTime = 1_000, breast = "LEFT")

        assertEquals(PumpingBreast.LEFT, entity.toDomain().breast)
    }

    @Test
    fun `toDomain falls back on unknown breast instead of crashing`() {
        val entity = PumpingEntity(startTime = 1_000, breast = "GARBAGE")

        assertEquals(PumpingBreast.BOTH, entity.toDomain().breast)
    }
}
