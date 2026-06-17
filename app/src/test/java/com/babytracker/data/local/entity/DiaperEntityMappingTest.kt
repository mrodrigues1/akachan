package com.babytracker.data.local.entity

import com.babytracker.domain.model.DiaperChange
import com.babytracker.domain.model.DiaperType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class DiaperEntityMappingTest {
    @Test
    fun `domain to entity to domain round trips`() {
        val change = DiaperChange(
            id = 7,
            timestamp = Instant.ofEpochMilli(1_000),
            type = DiaperType.BOTH,
            notes = "blowout",
            createdAt = Instant.ofEpochMilli(2_000),
        )
        assertEquals(change, change.toEntity().toDomain())
    }

    @Test
    fun `unknown stored type maps to WET`() {
        val entity = DiaperEntity(id = 1, timestamp = 0, type = "???", notes = null, createdAt = 0)
        assertEquals(DiaperType.WET, entity.toDomain().type)
    }
}
