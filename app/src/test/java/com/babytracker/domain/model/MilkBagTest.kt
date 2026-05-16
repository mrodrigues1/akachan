package com.babytracker.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class MilkBagTest {
    private val now = Instant.parse("2026-05-16T10:00:00Z")

    @Test
    fun `isActive true when usedAt null`() {
        val bag = MilkBag(collectionDate = now, volumeMl = 120, createdAt = now)
        assertTrue(bag.isActive)
    }

    @Test
    fun `isActive false when usedAt set`() {
        val bag = MilkBag(collectionDate = now, volumeMl = 120, createdAt = now, usedAt = now)
        assertFalse(bag.isActive)
    }

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
