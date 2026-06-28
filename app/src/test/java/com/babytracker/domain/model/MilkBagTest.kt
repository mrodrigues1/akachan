package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

class MilkBagTest {
    private val now = Instant.parse("2026-05-16T10:00:00Z")

    @Test
    fun `sourceSessionId preserved through copy`() {
        val bag = MilkBag(
            collectionDate = now,
            volumeMl = 120,
            sourceSessionId = 42L,
            createdAt = now,
        )
        assertEquals(42L, bag.sourceSessionId)
        assertEquals(42L, bag.copy(volumeMl = 150).sourceSessionId)
    }
}
